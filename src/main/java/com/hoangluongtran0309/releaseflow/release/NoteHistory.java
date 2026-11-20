package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.automation.NoteDeliveries;

import java.util.ArrayList;
import java.util.List;

/**
 * What a published note's page shows of its history (ADR-0046): every revision, newest
 * first, each with its difference from the one before, and every destination with the
 * revision it last received.
 */
public record NoteHistory(List<Entry> revisions, List<NoteDeliveries.Delivery> deliveries, int latest) {

    /** One revision and its difference from the one before; revision 1 has none. */
    public record Entry(ReleaseNoteRevisionView revision, List<LineDiff.Line> difference) {

        public boolean corrected() {
            return revision.revision() > 1;
        }
    }

    static NoteHistory of(List<ReleaseNoteRevisionView> oldestFirst, List<NoteDeliveries.Delivery> deliveries) {
        List<Entry> entries = new ArrayList<>();
        ReleaseNoteRevisionView previous = null;
        for (ReleaseNoteRevisionView revision : oldestFirst) {
            entries.addFirst(new Entry(revision,
                    previous == null ? List.of() : LineDiff.between(previous.content(), revision.content())));
            previous = revision;
        }
        return new NoteHistory(List.copyOf(entries), List.copyOf(deliveries),
                previous == null ? 1 : previous.revision());
    }

    /** Whether a destination still holds an earlier revision than the note's latest. */
    public boolean behind(NoteDeliveries.Delivery delivery) {
        return delivery.revision() < latest;
    }
}
