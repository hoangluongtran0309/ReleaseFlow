package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.automation.AutomationIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public entries made the way production makes them, a rule publishing a release, and
 * copies of them where a test needs an entry it can name and date itself.
 */
abstract class PublicChangelogTestBase extends AutomationIntegrationTestBase {

    protected String changelogRule(String name, UUID projectId, UUID audienceId, String language) {
        return """
                {"name":"%s","triggerType":"RELEASE_PUBLISHED","projectId":"%s",
                 "actions":[{"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"%s"}]}
                """.formatted(name, projectId, audienceId, language);
    }

    /** A release a rule published to the public changelog, in English, for end users. */
    protected UUID publishedEntry(Owner owner, UUID projectId, String version) throws Exception {
        UUID ruleId = createdRuleId(createRule(owner,
                changelogRule("Publish " + version, projectId, audienceId(owner, "end_user"), "en"))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        publishedRelease(owner, projectId, version);
        deliverEverything();
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public_changelog_entries ORDER BY published_at DESC, id DESC LIMIT 1", UUID.class);
    }

    protected String slug(Owner owner) throws Exception {
        String body = mockMvc.perform(get("/api/organization/slug").session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slug");
    }

    protected void changeSlug(Owner owner, String slug) throws Exception {
        mockMvc.perform(put("/api/organization/slug").session(owner.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"%s\"}".formatted(slug)))
                .andExpect(status().isOk());
    }

    /**
     * Another entry like {@code entryId}, delivered by a copy of its Action run, with the
     * columns named replaced: its ID, address, wording, and time are the test's own.
     */
    protected void copyEntry(UUID entryId, UUID copyId, int position, Map<String, Object> overrides) {
        UUID actionRunId = UUID.randomUUID();
        UUID sourceRun = jdbcTemplate.queryForObject(
                "SELECT action_run_id FROM public_changelog_entries WHERE id = ?", UUID.class, entryId);
        copy("automation_action_runs", sourceRun, Map.of("id", actionRunId, "position", position));
        Map<String, Object> entry = new LinkedHashMap<>(overrides);
        entry.put("id", copyId);
        entry.put("action_run_id", actionRunId);
        copy("public_changelog_entries", entryId, entry);
    }

    /**
     * The rows a correction run leaves behind (ADR-0046, part 5), written here because
     * that run comes in the next part: a {@code CORRECTION} run of the rule that published
     * the entry, asked for by {@code initiatorId} under {@code initiatorName}, its action
     * run carrying the note's revision {@code noteRevision}, and the entry's next revision.
     * V40's checks and triggers apply to every row, so nothing here can number a revision
     * out of turn or attach it to another Organization's entry.
     */
    protected int correct(
            UUID entryId,
            String content,
            String reason,
            Instant correctedAt,
            int noteRevision,
            UUID initiatorId,
            String initiatorName
    ) {
        Map<String, Object> entry = jdbcTemplate.queryForMap(
                "SELECT organization_id, action_run_id FROM public_changelog_entries WHERE id = ?", entryId);
        UUID deliveringAction = (UUID) entry.get("action_run_id");
        UUID deliveringRun = jdbcTemplate.queryForObject(
                "SELECT run_id FROM automation_action_runs WHERE id = ?", UUID.class, deliveringAction);
        UUID runId = UUID.randomUUID();
        UUID actionRunId = UUID.randomUUID();
        copy("automation_runs", deliveringRun, Map.of(
                "id", runId,
                "trigger_type", "CORRECTION",
                "request_id", UUID.randomUUID(),
                "initiated_by", initiatorId,
                "initiator_name", initiatorName));
        copy("automation_action_runs", deliveringAction, Map.of(
                "id", actionRunId,
                "run_id", runId,
                "note_revision", noteRevision));
        int revision = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revision), 1) + 1 FROM public_changelog_entry_revisions WHERE entry_id = ?",
                Integer.class, entryId);
        jdbcTemplate.update("""
                INSERT INTO public_changelog_entry_revisions
                    (id, entry_id, organization_id, revision, note_revision, action_run_id, content_snapshot, reason,
                     corrected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), entryId, entry.get("organization_id"), revision, noteRevision, actionRunId,
                content, reason, at(correctedAt));
        return revision;
    }

    protected static Timestamp at(Instant instant) {
        return Timestamp.from(instant);
    }

    // A row of the table as it is, but for the columns named.
    protected void copy(String table, UUID id, Map<String, Object> overrides) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position
                """, String.class, table);
        Map<String, Object> ordered = new LinkedHashMap<>();
        columns.stream().filter(overrides::containsKey).forEach(column -> ordered.put(column, overrides.get(column)));
        String select = columns.stream()
                .map(column -> overrides.containsKey(column) ? "?" : column)
                .collect(Collectors.joining(", "));
        jdbcTemplate.update("INSERT INTO " + table + " (" + String.join(", ", columns) + ") SELECT " + select
                + " FROM " + table + " WHERE id = ?", Stream.concat(ordered.values().stream(), Stream.of(id)).toArray());
    }
}
