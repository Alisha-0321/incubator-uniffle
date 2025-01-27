/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.uniffle.common.util;

import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.uniffle.common.exception.NotRetryException;

public class RetryUtils {
  private static final Logger LOG = LoggerFactory.getLogger(RetryUtils.class);

  public static <T> T retry(RetryCmd<T> cmd, long intervalMs, int retryTimes) throws Throwable {
    return retry(cmd, null, intervalMs, retryTimes, null);
  }

  public static <T> T retry(
      RetryCmd<T> cmd,
      long intervalMs,
      int retryTimes,
      Set<Class<? extends Throwable>> exceptionClasses)
      throws Throwable {
    return retry(cmd, null, intervalMs, retryTimes, exceptionClasses);
  }

  /**
   * @param cmd command to execute
   * @param callBack the callback command executed when the attempt of command fail
   * @param intervalMs retry interval
   * @param retryTimes retry times
   * @param exceptionClasses exception classes which need to be retry, null for all.
   * @param <T> return type
   * @return
   * @throws Throwable
   */
  public static <T> T retry(
      RetryCmd<T> cmd,
      RetryCallBack callBack,
      long intervalMs,
      int retryTimes,
      Set<Class<? extends Throwable>> exceptionClasses)
      throws Throwable {
    return retryWithCondition(
        cmd,
        callBack,
        intervalMs,
        retryTimes,
        t ->
            (exceptionClasses != null && isInstanceOf(exceptionClasses, t))
                || !(t instanceof NotRetryException));
  }

  public static <T> T retryWithCondition(
      RetryCmd<T> cmd,
      RetryCallBack callBack,
      long intervalMs,
      int retryTimes,
      Function<Throwable, Boolean> isRetryFunc)
      throws Throwable {
    int retry = 0;
    while (true) {
      try {
        return cmd.execute();
      } catch (Throwable t) {
        retry++;
        if (isRetryFunc.apply(t) && retry < retryTimes) {
          if (LOG.isDebugEnabled()) {
            LOG.error("Retry due to Throwable ", t);
          } else {
            LOG.error(
                "Retry due to Throwable {}. Use DEBUG level to see the full stack: {}",
                t.getClass().getName(),
                t.getMessage());
          }
          LOG.error(
              "Will retry {} more time(s) after waiting {} milliseconds.",
              retryTimes - retry,
              intervalMs);
          Thread.sleep(intervalMs);
          if (callBack != null) {
            callBack.execute();
          }
        } else {
          throw t;
        }
      }
    }
  }

  private static boolean isInstanceOf(Set<Class<? extends Throwable>> classes, Throwable t) {
    for (Class<? extends Throwable> c : classes) {
      if (c.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  public interface RetryCmd<T> {
    T execute() throws Throwable;
  }

  public interface RetryCallBack {
    void execute() throws Throwable;
  }
}
