package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

import java.util.Optional;
import java.util.OptionalInt;

/** A rule or one of its actions was described in a way automation cannot carry out. */
public class AutomationActionInvalidException extends LocalizedException {

    private final String code;
    private int actionPosition = -1;

    private AutomationActionInvalidException(String code, Object... arguments) {
        super("error." + code, arguments);
        this.code = code;
    }

    private AutomationActionInvalidException(String code, String messageKey) {
        super(messageKey);
        this.code = code;
    }

    static AutomationActionInvalidException actionRequired() {
        return new AutomationActionInvalidException("automation_action_required");
    }

    /**
     * One action that was begun but not finished: a caller still matches the same code, and
     * the wording names the part that is missing rather than claiming there is no action.
     */
    static AutomationActionInvalidException actionIncomplete(boolean typeMissing) {
        return new AutomationActionInvalidException(
                "automation_action_required",
                typeMissing ? "error.automation_action_required.type" : "error.automation_action_required.audience"
        );
    }

    static AutomationActionInvalidException projectNotFound() {
        return new AutomationActionInvalidException("automation_project_not_found");
    }

    static AutomationActionInvalidException audienceNotFound() {
        return new AutomationActionInvalidException("automation_audience_not_found");
    }

    static AutomationActionInvalidException languageNotConfigured(String language) {
        return new AutomationActionInvalidException(
                "automation_language_not_configured", (Object) language
        );
    }

    static AutomationActionInvalidException secretNotAllowed(ActionType actionType) {
        return new AutomationActionInvalidException(
                "automation_secret_not_allowed",
                switch (actionType) {
                    case GITHUB_RELEASE -> "error.automation_secret_not_allowed.githubRelease";
                    case EMAIL -> "error.automation_secret_not_allowed.email";
                    case SLACK -> "error.automation_secret_not_allowed.slack";
                    case PUBLIC_CHANGELOG -> "error.automation_secret_not_allowed.publicChangelog";
                    // These do take one, so nothing may report that theirs was refused.
                    case NOTION, CONFLUENCE, MICROSOFT_TEAMS, ZENDESK -> throw new IllegalStateException(
                            actionType + " takes a secret of its own.");
                }
        );
    }

    static AutomationActionInvalidException slackWebhookRequired() {
        return new AutomationActionInvalidException("automation_slack_webhook_required");
    }

    static AutomationActionInvalidException slackWebhookInvalid() {
        return new AutomationActionInvalidException("automation_slack_webhook_invalid");
    }

    static AutomationActionInvalidException emailRecipientsRequired() {
        return new AutomationActionInvalidException(
                "automation_email_recipients_required", EmailRecipients.MAX_RECIPIENTS
        );
    }

    static AutomationActionInvalidException emailRecipientInvalid(String recipient) {
        return new AutomationActionInvalidException(
                "automation_email_recipient_invalid", (Object) recipient
        );
    }

    static AutomationActionInvalidException notionParentRequired() {
        return new AutomationActionInvalidException("automation_notion_parent_required");
    }

    static AutomationActionInvalidException notionParentInvalid() {
        return new AutomationActionInvalidException("automation_notion_parent_invalid");
    }

    static AutomationActionInvalidException notionTokenRequired() {
        return new AutomationActionInvalidException("automation_notion_token_required");
    }

    static AutomationActionInvalidException confluenceSiteRequired() {
        return new AutomationActionInvalidException("automation_confluence_site_required");
    }

    static AutomationActionInvalidException confluenceSiteInvalid() {
        return new AutomationActionInvalidException("automation_confluence_site_invalid");
    }

    static AutomationActionInvalidException confluenceEmailRequired() {
        return new AutomationActionInvalidException("automation_confluence_email_required");
    }

    static AutomationActionInvalidException confluenceEmailInvalid() {
        return new AutomationActionInvalidException("automation_confluence_email_invalid");
    }

    static AutomationActionInvalidException confluenceSpaceRequired() {
        return new AutomationActionInvalidException("automation_confluence_space_required");
    }

    static AutomationActionInvalidException confluenceSpaceInvalid() {
        return new AutomationActionInvalidException("automation_confluence_space_invalid");
    }

    static AutomationActionInvalidException confluenceParentInvalid() {
        return new AutomationActionInvalidException("automation_confluence_parent_invalid");
    }

    static AutomationActionInvalidException confluenceTokenRequired() {
        return new AutomationActionInvalidException("automation_confluence_token_required");
    }

    static AutomationActionInvalidException teamsWebhookRequired() {
        return new AutomationActionInvalidException("automation_teams_webhook_required");
    }

    static AutomationActionInvalidException teamsWebhookInvalid() {
        return new AutomationActionInvalidException("automation_teams_webhook_invalid");
    }

    static AutomationActionInvalidException zendeskSubdomainRequired() {
        return new AutomationActionInvalidException("automation_zendesk_subdomain_required");
    }

    static AutomationActionInvalidException zendeskSubdomainInvalid() {
        return new AutomationActionInvalidException("automation_zendesk_subdomain_invalid");
    }

    static AutomationActionInvalidException zendeskClientIdRequired() {
        return new AutomationActionInvalidException("automation_zendesk_client_id_required");
    }

    static AutomationActionInvalidException zendeskClientSecretRequired() {
        return new AutomationActionInvalidException("automation_zendesk_client_secret_required");
    }

    static AutomationActionInvalidException zendeskSectionRequired() {
        return new AutomationActionInvalidException("automation_zendesk_section_required");
    }

    static AutomationActionInvalidException zendeskSectionInvalid() {
        return new AutomationActionInvalidException("automation_zendesk_section_invalid");
    }

    static AutomationActionInvalidException zendeskUserSegmentInvalid() {
        return new AutomationActionInvalidException("automation_zendesk_user_segment_invalid");
    }

    static AutomationActionInvalidException cronInvalid() {
        return new AutomationActionInvalidException("automation_cron_invalid");
    }

    static AutomationActionInvalidException cronTimeZoneInvalid() {
        return new AutomationActionInvalidException("automation_cron_time_zone_invalid");
    }

    static AutomationActionInvalidException cronHasNoFutureOccurrence() {
        return new AutomationActionInvalidException("automation_cron_no_occurrence");
    }

    static AutomationActionInvalidException cronReleaseRequired() {
        return new AutomationActionInvalidException("automation_cron_release_required");
    }

    static AutomationActionInvalidException reminderDaysInvalid() {
        return new AutomationActionInvalidException("automation_reminder_days_invalid");
    }

    static AutomationActionInvalidException requestIdRequired() {
        return new AutomationActionInvalidException("automation_request_id_required");
    }

    public String code() {
        return code;
    }

    /**
     * The property of the rule form this is about, so a page can say it under that field:
     * a property of the rule itself, or of one action when {@link #actionPosition()} says
     * which. Empty for a failure of the rule as a whole.
     */
    public Optional<String> field() {
        return Optional.ofNullable(switch (code) {
            case "automation_action_required" -> switch (messageKey()) {
                case "error.automation_action_required.type" -> "actionType";
                case "error.automation_action_required.audience" -> "audienceId";
                default -> null;
            };
            case "automation_project_not_found" -> "projectId";
            case "automation_cron_release_required" -> "releaseId";
            case "automation_cron_invalid", "automation_cron_no_occurrence" -> "cronExpression";
            case "automation_cron_time_zone_invalid" -> "cronTimeZone";
            case "automation_reminder_days_invalid" -> "daysBefore";
            case "automation_audience_not_found" -> "audienceId";
            case "automation_language_not_configured" -> "language";
            case "automation_email_recipients_required", "automation_email_recipient_invalid" -> "recipients";
            case "automation_notion_parent_required", "automation_notion_parent_invalid",
                 "automation_confluence_parent_invalid" -> "parentPageId";
            case "automation_confluence_site_required", "automation_confluence_site_invalid" -> "siteUrl";
            case "automation_confluence_email_required", "automation_confluence_email_invalid" -> "email";
            case "automation_confluence_space_required", "automation_confluence_space_invalid" -> "spaceId";
            case "automation_zendesk_subdomain_required", "automation_zendesk_subdomain_invalid" -> "subdomain";
            case "automation_zendesk_client_id_required" -> "clientId";
            case "automation_zendesk_section_required", "automation_zendesk_section_invalid" -> "sectionId";
            case "automation_zendesk_user_segment_invalid" -> "userSegmentId";
            case "automation_secret_not_allowed", "automation_slack_webhook_required",
                 "automation_slack_webhook_invalid", "automation_notion_token_required",
                 "automation_confluence_token_required", "automation_teams_webhook_required",
                 "automation_teams_webhook_invalid", "automation_zendesk_client_secret_required" -> "secret";
            default -> null;
        });
    }

    /** Which of the described actions, counted from zero, this is about, if any. */
    public OptionalInt actionPosition() {
        return actionPosition < 0 ? OptionalInt.empty() : OptionalInt.of(actionPosition);
    }

    /** Says which described action failed; the service knows, the executor does not. */
    AutomationActionInvalidException inAction(int position) {
        this.actionPosition = position;
        return this;
    }
}
