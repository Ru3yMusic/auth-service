package com.rubymusic.auth.exception;

import com.rubymusic.auth.model.enums.BlockReason;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user whose status is BLOCKED attempts to log in.
 * Returns HTTP 403 with a response body that includes the block reason so
 * the frontend can show the "account blocked" modal with the real motive
 * chosen by the admin. See GlobalExceptionHandler#handleAccountBlocked.
 */
public class AccountBlockedException extends BaseAuthException {

    private final BlockReason blockReason;

    public AccountBlockedException(String message, BlockReason blockReason) {
        super(message, HttpStatus.FORBIDDEN);
        this.blockReason = blockReason;
    }

    public BlockReason getBlockReason() {
        return blockReason;
    }
}
