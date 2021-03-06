package com.sedmelluq.discord.lavaplayer.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for working with executors.
 */
public class ExecutorTools {
  private static final Logger log = LoggerFactory.getLogger(ExecutorTools.class);

  private static final long WAIT_TIME = 1000L;

  /**
   * Shut down an executor and log the shutdown result. The executor is given a fixed amount of time to shut down, if it
   * does not manage to do it in that time, then this method just returns.
   *
   * @param executorService Executor service to shut down
   * @param description Description of the service to use for logging
   */
  public static void shutdownExecutor(ExecutorService executorService, String description) {
    if (executorService == null) {
      return;
    }

    log.debug("Shutting down executor {}", description);

    executorService.shutdownNow();

    try {
      if (!executorService.awaitTermination(WAIT_TIME, TimeUnit.SECONDS)) {
        log.debug("Executor {} did not shut down in {}", description, WAIT_TIME);
      } else {
        log.debug("Executor {} successfully shut down", description);
      }
    } catch (InterruptedException e) {
      log.debug("Received an interruption while shutting down executor {}", description);
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Creates an executor which will use the queue only when maximum number of threads has been reached. The core pool
   * size here only means the number of threads that are always alive, it is no longer used to check whether a new
   * thread should start or not. The maximum size is otherwise pointless unless you have a bounded queue, which in turn
   * would cause tasks to be rejected if it is too small.
   *
   * @param coreSize Number of threads that are always alive
   * @param maximumSize The maximum number of threads in the pool
   * @param timeout Non-core thread timeout in milliseconds
   * @param poolName Name of the daemon thread pool to create
   * @return An eagerly scaling thread pool executor
   */
  public static ExecutorService createEagerlyScalingExecutor(int coreSize, int maximumSize, long timeout, String poolName) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(coreSize, maximumSize, timeout, TimeUnit.MILLISECONDS,
        new EagerlyScalingTaskQueue(), new DaemonThreadFactory(poolName));

    executor.setRejectedExecutionHandler(new EagerlyScalingRejectionHandler());
    return executor;
  }

  private static class EagerlyScalingTaskQueue extends LinkedBlockingQueue<Runnable> {
    @Override
    public boolean offer(Runnable runnable) {
      return isEmpty() && super.offer(runnable);
    }
  }

  private static class EagerlyScalingRejectionHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(runnable);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
