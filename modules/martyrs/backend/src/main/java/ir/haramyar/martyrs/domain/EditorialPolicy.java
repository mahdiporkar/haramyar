package ir.haramyar.martyrs.domain;

import ir.haramyar.martyrs.domain.Values.EditorialActor;

/**
 * Proposed editorial responsibilities (pending business confirmation): editors draft, revise and submit; reviewers
 * request changes, publish and unpublish. With {@code fourEyes} the reviewer who publishes must differ from the editor
 * who submitted. Either capacity may reopen an archived profile for revision.
 */
public record EditorialPolicy(boolean fourEyes) {
  public enum Operation { CREATE, REVISE, SUBMIT, REQUEST_CHANGES, PUBLISH, ARCHIVE, REOPEN }

  public boolean permits(Operation operation, EditorialActor.Role role) {
    return switch (operation) {
      case CREATE, REVISE, SUBMIT -> role == EditorialActor.Role.EDITOR;
      case REQUEST_CHANGES, PUBLISH, ARCHIVE -> role == EditorialActor.Role.REVIEWER;
      case REOPEN -> true;
    };
  }
}
