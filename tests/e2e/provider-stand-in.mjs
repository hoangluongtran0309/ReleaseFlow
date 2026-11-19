// Stands in for Jira Cloud, GitHub, and Linear in the browser suite, so a change can have a
// linked issue whose status is still open (ADR-0041). A change gets linked issues only when
// its code host has an access token, so GitHub is here too. Linear is here so a team can be
// connected, disconnected, and reconnected (ADR-0042).
//
// Under /jira it answers the three calls ReleaseFlow makes to Jira: the access check when a
// project is connected, the search a poll runs (nothing is done yet), and the read of one
// issue, always "In Progress". Under /github it answers the access check when a token is
// saved and a pull request's files and commits, all empty. Under /linear it answers the
// team lookup that confirms an API key, for any team, in one workspace, and any other query
// with no data. Anything else is a 404. The suite's Compose file points the three API base
// URLs here; nothing else ever does.
import { createServer } from 'node:http';

const JIRA_PROJECT = /^\/jira\/rest\/api\/3\/project\/([A-Z][A-Z0-9_]*)$/;
const JIRA_ISSUE = /^\/jira\/rest\/api\/3\/issue\/([A-Z][A-Z0-9_]*-[0-9]+)$/;
const GITHUB_PULLS = /^\/github\/repos\/[^/]+\/[^/]+\/pulls$/;
const GITHUB_PULL_LISTS = /^\/github\/repos\/[^/]+\/[^/]+\/pulls\/[0-9]+\/(files|commits)$/;
// The workspace every Linear team here belongs to; source-lifecycle.spec.js signs deliveries
// for it.
const LINEAR_WORKSPACE = '22222222-2222-4222-8222-222222222222';

function answer(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(body === undefined ? '' : JSON.stringify(body));
}

function jira(pathname, response) {
  const project = JIRA_PROJECT.exec(pathname);
  if (project) {
    return answer(response, 200, { key: project[1], name: project[1] });
  }
  if (pathname === '/jira/rest/api/3/search/jql') {
    return answer(response, 200, { issues: [], isLast: true });
  }
  const issue = JIRA_ISSUE.exec(pathname);
  if (issue) {
    return answer(response, 200, {
      key: issue[1],
      fields: {
        summary: 'Retention preview',
        description: null,
        issuetype: { name: 'Story' },
        status: { name: 'In Progress', statusCategory: { key: 'indeterminate' } },
      },
    });
  }
  return answer(response, 404, { errorMessages: ['Not found.'] });
}

function github(pathname, response) {
  if (GITHUB_PULLS.test(pathname) || GITHUB_PULL_LISTS.test(pathname)) {
    return answer(response, 200, []);
  }
  return answer(response, 404, { message: 'Not Found' });
}

function linear(request, response) {
  let body = '';
  request.setEncoding('utf8');
  request.on('data', (chunk) => {
    body += chunk;
  });
  request.on('end', () => {
    let document;
    try {
      document = JSON.parse(body);
    } catch {
      return answer(response, 400, { errors: [{ message: 'Not JSON.' }] });
    }
    const id = document?.variables?.id;
    if (typeof document?.query === 'string' && document.query.includes('team(') && typeof id === 'string') {
      return answer(response, 200, { data: { team: { id, key: 'OPS', organization: { id: LINEAR_WORKSPACE } } } });
    }
    return answer(response, 200, { data: {} });
  });
}

const server = createServer((request, response) => {
  const { pathname } = new URL(request.url, 'http://stand-in.invalid');
  if (pathname === '/health') {
    return answer(response, 200, { status: 'UP' });
  }
  const authorization = request.headers.authorization ?? '';
  if (request.method === 'GET' && pathname.startsWith('/jira/') && authorization.startsWith('Basic ')) {
    return jira(pathname, response);
  }
  if (request.method === 'GET' && pathname.startsWith('/github/') && authorization.startsWith('Bearer ')) {
    return github(pathname, response);
  }
  if (request.method === 'POST' && pathname === '/linear/graphql' && authorization !== '') {
    return linear(request, response);
  }
  return answer(response, request.method === 'GET' ? 401 : 404, { message: 'Not supported here.' });
});

server.listen(8080, '0.0.0.0');
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
