package de.winlaufen.web.contract;

/**
 * Signals that a value does not satisfy the versioned contract.
 *
 * <p>This is a data problem, not a transport problem. Callers must be able to tell the two apart:
 * a contract violation is not repaired by reconnecting, so an output adapter has to skip the
 * offending revision instead of tearing its connection down.
 *
 * <p>It extends {@link IllegalArgumentException} so that existing callers which only distinguish
 * "bad argument" keep working unchanged.
 */
public class ContractViolationException extends IllegalArgumentException {
    public ContractViolationException(String message) { super(message); }
    public ContractViolationException(String message, Throwable cause) { super(message, cause); }
}
