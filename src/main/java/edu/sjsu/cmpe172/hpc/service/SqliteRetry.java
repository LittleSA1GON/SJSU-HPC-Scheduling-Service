package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class SqliteRetry {
    private static final Logger log = LoggerFactory.getLogger(SqliteRetry.class);
    public <T> T execute(Supplier<T> transaction) {
        // Calls a different bean's @Transactional method: each retry has a fresh snapshot.
        for (int attempt = 0; ; attempt++) {
            try { return transaction.get(); }
            catch (DataAccessException ex) {
                if (!isBusy(ex)) throw ex;
                if (attempt == 2) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The scheduler is busy. Please retry shortly.");
                log.warn("SQLite busy (attempt {}/3); retrying transaction", attempt + 1);
                try { Thread.sleep(25L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted. Please retry.");
                }
            }
        }
    }
    private boolean isBusy(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                int primaryCode = sql.getErrorCode() & 0xff;
                if (primaryCode == 5 || primaryCode == 6) return true; // BUSY, BUSY_SNAPSHOT, LOCKED
            }
        }
        return false;
    }
}
