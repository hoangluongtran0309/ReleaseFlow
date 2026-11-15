package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final IntegrationSourceRepository sourceRepository;
    private final SourceViews sourceViews;
    private final Clock clock;

    ProjectService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            SourceViews sourceViews,
            Clock clock
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.sourceViews = sourceViews;
        this.clock = clock;
    }

    @Transactional
    ProjectView create(UUID organizationId, ProjectRequest request) {
        Instant createdAt = clock.instant();
        Project project = projectRepository.save(new Project(
                UUID.randomUUID(),
                organizationId,
                request.getName(),
                createdAt
        ));
        return toView(project, List.of());
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID organizationId, UUID projectId) {
        Project project = find(organizationId, projectId);
        return toView(
                project,
                sourceViews.views(
                        organizationId,
                        sourceRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(organizationId, projectId)
                )
        );
    }

    /**
     * What a Project's administrators wrote about its product, or empty. The AI receives
     * it with every change it classifies, and DeepL with every translation (ADR-0035).
     */
    @Transactional(readOnly = true)
    public String context(UUID organizationId, UUID projectId) {
        return find(organizationId, projectId).getContext();
    }

    @Transactional(readOnly = true)
    public ProjectContextView contextView(UUID organizationId, UUID projectId) {
        return contextView(find(organizationId, projectId));
    }

    // Changes already classified keep what the AI wrote; nothing is classified again.
    @Transactional
    public ProjectContextView replaceContext(
            ReleaseFlowPrincipal administrator,
            UUID projectId,
            ProjectContextRequest request
    ) {
        Project project = find(administrator.organizationId(), projectId);
        project.replaceContext(request.getContext(), administrator.userId(), administrator.displayName(), clock.instant());
        return contextView(projectRepository.saveAndFlush(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UUID organizationId) {
        List<Project> projects = projectRepository.findAllByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
        if (projects.isEmpty()) {
            return List.of();
        }

        List<IntegrationSource> sources = sourceRepository.findAllByOrganizationIdAndProjectIdInOrderByCreatedAtAscIdAsc(
                organizationId,
                projects.stream().map(Project::getId).toList()
        );
        // One rhythm query for every source of every Project.
        Map<UUID, IntegrationSourceView> views = sourceViews.byId(organizationId, sources);
        Map<UUID, List<IntegrationSourceView>> sourcesByProject = sources.stream()
                .collect(Collectors.groupingBy(
                        IntegrationSource::getProjectId,
                        Collectors.mapping(source -> views.get(source.getId()), Collectors.toList())
                ));

        return projects.stream()
                .map(project -> toView(project, sourcesByProject.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    private Project find(UUID organizationId, UUID projectId) {
        return projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
    }

    private static ProjectContextView contextView(Project project) {
        return new ProjectContextView(
                project.getId(),
                project.getContext(),
                project.getContextUpdaterName(),
                project.getContextUpdatedAt()
        );
    }

    private static ProjectView toView(Project project, List<IntegrationSourceView> sources) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                sources
        );
    }
}
