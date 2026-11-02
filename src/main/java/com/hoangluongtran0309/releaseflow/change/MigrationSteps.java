package com.hoangluongtran0309.releaseflow.change;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Drops an AI's migration step that only says nothing needs doing (ADR-0035). Such a
 * step is no step: read as one, none with a step would become recommended. A fact
 * written beside the verdict, such as that existing passwords move to the new key when
 * edited, belongs in limitations, where the AI puts it too. A step keeps its place when
 * the verdict is qualified ("unless", "if", "for most"), comes after something to do ("no
 * further action"), or sits beside a clause that still asks something ("must", "should", "rename").
 * Only an AI answer is filtered: a person's words stay as written.
 */
final class MigrationSteps {

    // English and Vietnamese, the languages ReleaseFlow writes its own text in.
    private static final Pattern NOTHING_TO_DO = Pattern.compile(
            "^(?:there (?:is|are) )?no (?:immediate |manual |direct |explicit |specific |user |operator )*"
                    + "(?:action|actions|migration|migrations|migration steps?|steps?|changes?|configuration changes?|upgrade steps?)"
                    + " (?:is |are )?(?:required|needed|necessary)\\b"
                    + "|^no need to\\b"
                    + "|^nothing (?:needs to be done|to do|is required|is needed)\\b"
                    + "|^(?:this|the) (?:change|update|upgrade|release)"
                    + " (?:does not|doesn't) require any (?:action|migration|manual|changes|steps)\\b"
                    + "|^(?:this|the) (?:change|update|upgrade|release) requires no (?:action|migration|manual|changes|steps)\\b"
                    + "|^(?:none|n/?a)$"
                    + "|^không cần (?:thực hiện |làm )?(?:thao tác|hành động|bước|di chuyển|chuyển đổi|thay đổi|làm gì|gì)"
                    + "|^không yêu cầu (?:thao tác|hành động|bước|di chuyển|chuyển đổi|thay đổi)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUALIFIED = Pattern.compile(
            "\\b(?:unless|if|except|but|however|until|when|before|while|provided|most|some|only)\\b"
                    + "|trừ khi|nếu|ngoại trừ|nhưng|cho tới khi|cho đến khi",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ASKS = Pattern.compile(
            "\\b(?:must|should|need to|needs to|have to|has to|required to)\\b"
                    + "|^(?:rename|set|update|apply|run|replace|remove|add|configure|enable|disable|change|upgrade"
                    + "|install|migrate|re-?save|edit|review|ensure|move|delete|restart|regenerate|rotate|switch|use)\\b"
                    + "|cần phải|bắt buộc phải",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CLAUSE_END = Pattern.compile("[.!?;\\n]+");
    private static final Pattern LIST_ITEM = Pattern.compile("^([-*+]|\\d+[.)])\\s+");

    private MigrationSteps() {
    }

    /** The step, or empty when it says, unqualified, that nothing needs doing and asks nothing else. */
    static String stated(String step) {
        List<String> clauses = Arrays.stream(CLAUSE_END.split(step))
                .map(clause -> LIST_ITEM.matcher(clause.strip()).replaceFirst(""))
                .filter(clause -> !clause.isEmpty())
                .toList();
        List<String> verdicts = clauses.stream()
                .filter(clause -> NOTHING_TO_DO.matcher(clause).find() && !QUALIFIED.matcher(clause).find())
                .toList();
        boolean asksSomething = clauses.stream()
                .filter(clause -> !verdicts.contains(clause))
                .anyMatch(clause -> ASKS.matcher(clause).find());
        return verdicts.isEmpty() || asksSomething ? step : "";
    }
}
