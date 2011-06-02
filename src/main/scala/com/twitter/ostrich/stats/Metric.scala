/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich
package stats

import scala.collection.mutable
import com.twitter.logging.Logger

/**
 * A Metric collates data points and can report a Distribution.
 */
class Metric {
  val log = Logger.get(getClass.getName)

  private var sum: Int = 0
  private var count: Int = 0
  private var histogram = new Histogram()

  /**
   * Resets the state of this Metric. Clears all data points collected so far.
   */
  def clear() = synchronized {
    sum = 0
    count = 0
    histogram.clear()
  }

  /**
   * Adds a data point.
   */
  def add(n: Int): Long = {
    if (n > -1) {
      val histogramBucketIndex = Histogram.bucketIndex(n)
      synchronized {
        sum += n
        count += 1
        histogram.addToBucket(histogramBucketIndex)
        count
      }
    } else {
      log.warning("Tried to add a negative data point.")
      count
    }
  }

  /**
   * Add a summarized set of data points.
   */
  def add(distribution: Distribution): Long = synchronized {
    if (distribution.count > 0) {
      count += distribution.count
      sum += distribution.sum
      distribution.histogram.map { h => histogram.merge(h) }
    }
    count
  }

  def since(previous: Metric): Distribution = {
    val h = histogram - previous.histogram
    new Distribution(count - previous.count, h.maximum, h.minimum, Some(h), sum - previous.sum)
  }

  /**
   * Returns a Distribution for this Metric.
   */
  def apply(reset: Boolean): Distribution = synchronized {
    val rv = new Distribution(
      count,
      histogram.maximum,
      histogram.minimum,
      Some(histogram.clone()),
      sum)
    if (reset) clear()
    rv
  }
}

class FanoutMetric(others: Metric*) extends Metric {
  private val fanout = new mutable.HashSet[Metric]
  others.foreach { metric => addFanout(metric) }

  def addFanout(metric: Metric) {
    fanout += metric
  }

  override def clear() {
    synchronized {
      super.clear()
      fanout.foreach { _.clear() }
    }
  }

  override def add(n: Int) = synchronized {
    fanout.foreach { _.add(n) }
    super.add(n)
  }

  override def add(distribution: Distribution) = synchronized {
    fanout.foreach { _.add(distribution) }
    super.add(distribution)
  }
}
