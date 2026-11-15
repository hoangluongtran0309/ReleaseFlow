package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void allowsSeveralSourcesPerProjectButARepositoryOncePerOrganization() {
        UUID firstOrganization = insertOrganization("First");
        UUID secondOrganization = insertOrganization("Second");
        UUID firstProject = insertProject(firstOrganization, "First project");
        UUID anotherFirstProject = insertProject(firstOrganization, "Another first project");
        UUID secondProject = insertProject(secondOrganization, "Second project");
        insertIntegration(firstOrganization, firstProject, "acme", "releaseflow", validNonce(), validCiphertext());

        assertThatCode(() -> insertIntegration(
                firstOrganization, firstProject, "other", "repository", validNonce(), validCiphertext()
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertIntegration(
                firstOrganization, anotherFirstProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertIntegration(
                secondOrganization, secondProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).doesNotThrowAnyException();
    }

    @Test
    void aDisconnectedSourceHoldsNoCredentialAndFreesItsRepository() {
        UUID organization = insertOrganization("First");
        UUID project = insertProject(organization, "First project");
        UUID anotherProject = insertProject(organization, "Another project");
        UUID administrator = insertAdministrator(organization);
        UUID disconnected = insertIntegration(organization, project, "acme", "releaseflow", validNonce(), validCiphertext());
        String clean = """
                UPDATE integration_sources SET webhook_id = NULL, secret_nonce = NULL, secret_ciphertext = NULL,
                    disconnected_at = now(), disconnected_by = ? WHERE id = ?""";

        // Disconnecting while keeping the secret, or without saying who, is refused.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE integration_sources SET disconnected_at = now(), disconnected_by = ? WHERE id = ?",
                administrator, disconnected)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE integration_sources SET webhook_id = NULL, secret_nonce = NULL, secret_ciphertext = NULL,
                    disconnected_at = now() WHERE id = ?""", disconnected))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(clean, administrator, disconnected)).doesNotThrowAnyException();

        // Another Project may now connect the repository; the same Project holds it only once.
        assertThatCode(() -> insertIntegration(
                organization, anotherProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertIntegration(
                organization, project, "acme", "releaseflow", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCrossTenantProjectLinksAndInvalidEncryptionEnvelope() {
        UUID firstOrganization = insertOrganization("First");
        UUID secondOrganization = insertOrganization("Second");
        UUID firstProject = insertProject(firstOrganization, "First project");

        assertThatThrownBy(() -> insertIntegration(
                secondOrganization, firstProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIntegration(
                firstOrganization, firstProject, "acme", "releaseflow", new byte[11], validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOrganization(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, created_at, output_language) VALUES (?, ?, 'org-' || SUBSTRING(gen_random_uuid()::text, 1, 8), ?, 'en')",
                id,
                name,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private UUID insertProject(UUID organizationId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, ?, ?)",
                id,
                organizationId,
                name,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private UUID insertAdministrator(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                VALUES (?, ?, 'admin@example.com', 'x', 'Admin', 'ADMIN', now())""", id, organizationId);
        return id;
    }

    private UUID insertIntegration(
            UUID organizationId,
            UUID projectId,
            String owner,
            String repository,
            byte[] nonce,
            byte[] ciphertext
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO integration_sources
                            (id, organization_id, project_id, source_type, external_project_key, repository_owner,
                             repository_name, webhook_auth_mode, webhook_id, secret_nonce, secret_ciphertext, created_at)
                        VALUES (?, ?, ?, 'GITHUB', ?, ?, ?, 'GITHUB_HMAC', ?, ?, ?, ?)
                        """,
                id,
                organizationId,
                projectId,
                owner + "/" + repository,
                owner,
                repository,
                UUID.randomUUID(),
                nonce,
                ciphertext,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private static byte[] validNonce() {
        return new byte[12];
    }

    private static byte[] validCiphertext() {
        return new byte[17];
    }
}
