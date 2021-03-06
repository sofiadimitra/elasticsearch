/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.reindex;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.test.ESTestCase;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.elasticsearch.common.unit.TimeValue.timeValueMillis;
import static org.elasticsearch.index.reindex.TransportRethrottleActionTests.captureResponse;
import static org.elasticsearch.index.reindex.TransportRethrottleActionTests.neverCalled;
import static org.mockito.Mockito.mock;

public class ParentBulkByScrollTaskTests extends ESTestCase {
    private int slices;
    private ParentBulkByScrollTask task;

    @Before
    public void createTask() {
        slices = between(2, 50);
        task = new ParentBulkByScrollTask(1, "test_type", "test_action", "test", null, slices);
    }

    public void testBasicData() {
        assertEquals(1, task.getId());
        assertEquals("test_type", task.getType());
        assertEquals("test_action", task.getAction());
        assertEquals("test", task.getDescription());
    }

    public void testProgress() {
        long total = 0;
        long created = 0;
        long updated = 0;
        long deleted = 0;
        long noops = 0;
        long versionConflicts = 0;
        int batches = 0;
        List<BulkByScrollTask.StatusOrException> sliceStatuses = Arrays.asList(new BulkByScrollTask.StatusOrException[slices]);
        BulkByScrollTask.Status status = task.getStatus();
        assertEquals(total, status.getTotal());
        assertEquals(created, status.getCreated());
        assertEquals(updated, status.getUpdated());
        assertEquals(deleted, status.getDeleted());
        assertEquals(noops, status.getNoops());
        assertEquals(versionConflicts, status.getVersionConflicts());
        assertEquals(batches, status.getBatches());
        assertEquals(sliceStatuses, status.getSliceStatuses());

        for (int slice = 0; slice < slices; slice++) {
            int thisTotal = between(10, 10000);
            int thisCreated = between(0, thisTotal);
            int thisUpdated = between(0, thisTotal - thisCreated);
            int thisDeleted = between(0, thisTotal - thisCreated - thisUpdated);
            int thisNoops = thisTotal - thisCreated - thisUpdated - thisDeleted;
            int thisVersionConflicts = between(0, 1000);
            int thisBatches = between(1, 100);
            BulkByScrollTask.Status sliceStatus = new BulkByScrollTask.Status(slice, thisTotal, thisUpdated, thisCreated, thisDeleted,
                    thisBatches, thisVersionConflicts, thisNoops, 0, 0, timeValueMillis(0), 0, null, timeValueMillis(0));
            total += thisTotal;
            created += thisCreated;
            updated += thisUpdated;
            deleted += thisDeleted;
            noops += thisNoops;
            versionConflicts += thisVersionConflicts;
            batches += thisBatches;
            sliceStatuses.set(slice, new BulkByScrollTask.StatusOrException(sliceStatus));

            @SuppressWarnings("unchecked")
            ActionListener<BulkIndexByScrollResponse> listener = slice < slices - 1 ? neverCalled() : mock(ActionListener.class);
            task.onSliceResponse(listener, slice,
                    new BulkIndexByScrollResponse(timeValueMillis(10), sliceStatus, emptyList(), emptyList(), false));

            status = task.getStatus();
            assertEquals(total, status.getTotal());
            assertEquals(created, status.getCreated());
            assertEquals(updated, status.getUpdated());
            assertEquals(deleted, status.getDeleted());
            assertEquals(versionConflicts, status.getVersionConflicts());
            assertEquals(batches, status.getBatches());
            assertEquals(noops, status.getNoops());
            assertEquals(sliceStatuses, status.getSliceStatuses());

            if (slice == slices - 1) {
                // The whole thing succeeded so we should have got the success
                status = captureResponse(BulkIndexByScrollResponse.class, listener).getStatus();
                assertEquals(total, status.getTotal());
                assertEquals(created, status.getCreated());
                assertEquals(updated, status.getUpdated());
                assertEquals(deleted, status.getDeleted());
                assertEquals(versionConflicts, status.getVersionConflicts());
                assertEquals(batches, status.getBatches());
                assertEquals(noops, status.getNoops());
                assertEquals(sliceStatuses, status.getSliceStatuses());
            }
        }
    }


}
