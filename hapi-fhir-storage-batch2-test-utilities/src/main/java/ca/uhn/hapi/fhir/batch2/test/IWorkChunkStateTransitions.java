/*-
 * #%L
 * HAPI FHIR JPA Server - Batch2 specification tests
 * %%
 * Copyright (C) 2014 - 2024 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.hapi.fhir.batch2.test;

import ca.uhn.fhir.batch2.model.JobDefinition;
import ca.uhn.fhir.batch2.model.WorkChunk;
import ca.uhn.fhir.batch2.model.WorkChunkCompletionEvent;
import ca.uhn.fhir.batch2.model.WorkChunkStatusEnum;
import ca.uhn.hapi.fhir.batch2.test.support.JobMaintenanceStateInformation;
import ca.uhn.hapi.fhir.batch2.test.support.TestJobParameters;
import ca.uhn.test.concurrency.PointcutLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public interface IWorkChunkStateTransitions extends IWorkChunkCommon, WorkChunkTestConstants {

	Logger ourLog = LoggerFactory.getLogger(IWorkChunkStateTransitions.class);

	@ParameterizedTest
	@CsvSource({
		"false, READY",
		"true, GATE_WAITING"
	})
	default void chunkCreation_nonFirstChunk_isInExpectedStatus(boolean theGatedExecution, WorkChunkStatusEnum expectedStatus) {
		String jobInstanceId = getTestManager().createAndStoreJobInstance(null);
		String myChunkId = getTestManager().createChunk(jobInstanceId, theGatedExecution);

		WorkChunk fetchedWorkChunk = getTestManager().freshFetchWorkChunk(myChunkId);
		assertEquals(expectedStatus, fetchedWorkChunk.getStatus(), "New chunks are " + expectedStatus);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	default void chunkCreation_firstChunk_isInReady(boolean theGatedExecution) {
		JobDefinition<TestJobParameters> jobDef = getTestManager().withJobDefinition(theGatedExecution);
		String jobInstanceId = getTestManager().createAndStoreJobInstance(jobDef);
		String myChunkId = getTestManager().createFirstChunk(jobDef, jobInstanceId);

		WorkChunk fetchedWorkChunk = getTestManager().freshFetchWorkChunk(myChunkId);
		assertEquals(WorkChunkStatusEnum.READY, fetchedWorkChunk.getStatus(), "New chunks are " + WorkChunkStatusEnum.READY);
	}

	@Test
	default void chunkReceived_forNongatedJob_queuedToInProgress() throws InterruptedException {
		PointcutLatch sendLatch = getTestManager().disableWorkChunkMessageHandler();
		sendLatch.setExpectedCount(1);

		JobDefinition<TestJobParameters> jobDef = getTestManager().withJobDefinition(false);
		String jobInstanceId = getTestManager().createAndStoreJobInstance(jobDef);
		String myChunkId = getTestManager().createChunk(jobInstanceId, false);

		getTestManager().runMaintenancePass();
		// the worker has received the chunk, and marks it started.
		WorkChunk chunk = getTestManager().getSvc().onWorkChunkDequeue(myChunkId).orElseThrow(IllegalArgumentException::new);

		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, chunk.getStatus());
		assertEquals(CHUNK_DATA, chunk.getData());

		// verify the db was updated too
		WorkChunk fetchedWorkChunk = getTestManager().freshFetchWorkChunk(myChunkId);
		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, fetchedWorkChunk.getStatus());
		getTestManager().verifyWorkChunkMessageHandlerCalled(sendLatch, 1);
	}

	@Test
	default void chunkReceived_forGatedJob_queuedToInProgress() throws InterruptedException {
		PointcutLatch sendLatch = getTestManager().disableWorkChunkMessageHandler();
		sendLatch.setExpectedCount(2);

		JobDefinition<TestJobParameters> jobDef = getTestManager().withJobDefinition(true);
		String jobInstanceId = getTestManager().createAndStoreJobInstance(jobDef);
		String chunkInStep1 = getTestManager().createFirstChunk(jobDef, jobInstanceId);
		String chunkInStep2 = getTestManager().createChunkInStep(jobInstanceId, SECOND_STEP_ID, true);

		// dequeue and completes the first chunk
		getTestManager().runMaintenancePass();
		// the worker has received the chunk, and marks it started.
		WorkChunk chunk1 = getTestManager().getSvc().onWorkChunkDequeue(chunkInStep1).orElseThrow(IllegalArgumentException::new);
		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, chunk1.getStatus());
		WorkChunk fetchedWorkChunk = getTestManager().freshFetchWorkChunk(chunkInStep1);
		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, fetchedWorkChunk.getStatus());

		getTestManager().getSvc().onWorkChunkCompletion(new WorkChunkCompletionEvent(chunkInStep1, 50, 0));
		fetchedWorkChunk = getTestManager().freshFetchWorkChunk(chunkInStep1);
		assertEquals(WorkChunkStatusEnum.COMPLETED, fetchedWorkChunk.getStatus());

		// dequeue the second chunk
		getTestManager().runMaintenancePass();
		WorkChunk chunk2 = getTestManager().getSvc().onWorkChunkDequeue(chunkInStep2).orElseThrow(IllegalArgumentException::new);
		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, chunk2.getStatus());
		assertEquals(CHUNK_DATA, chunk2.getData());

		WorkChunk fetchedWorkChunk2 = getTestManager().freshFetchWorkChunk(chunkInStep2);
		assertEquals(WorkChunkStatusEnum.IN_PROGRESS, fetchedWorkChunk2.getStatus());

		getTestManager().verifyWorkChunkMessageHandlerCalled(sendLatch, 2);
	}

	@Test
	default void enqueueWorkChunkForProcessing_enqueuesOnlyREADYChunks() throws InterruptedException {
		// setup
		getTestManager().disableWorkChunkMessageHandler();
		getTestManager().enableMaintenanceRunner(false);

		StringBuilder sb = new StringBuilder();
		// first step is always complete
		sb.append("1|COMPLETED");
		for (WorkChunkStatusEnum status : WorkChunkStatusEnum.values()) {
			if (!sb.isEmpty()) {
				sb.append("\n");
			}
			// second step for all other workchunks
			sb.append("2|")
				.append(status.name());
		}
		String state = sb.toString();
		JobDefinition<?> jobDef = getTestManager().withJobDefinition(false);
		String instanceId = getTestManager().createAndStoreJobInstance(jobDef);
		JobMaintenanceStateInformation stateInformation = new JobMaintenanceStateInformation(
			instanceId,
			jobDef,
			state
		);
		getTestManager().createChunksInStates(stateInformation);

		// test
		PointcutLatch latch = new PointcutLatch(new Exception().getStackTrace()[0].getMethodName());
		latch.setExpectedCount(stateInformation.getInitialWorkChunks().size());
		for (WorkChunk chunk : stateInformation.getInitialWorkChunks()) {
			getTestManager().getSvc().enqueueWorkChunkForProcessing(chunk.getId(), updated -> {
				// should not update non-ready chunks
				ourLog.info("Enqueuing chunk with state {}; updated {}", chunk.getStatus().name(), updated);
				int expected = chunk.getStatus() == WorkChunkStatusEnum.READY ? 1 : 0;
				assertEquals(expected, updated);
				latch.call(1);
			});
		}
		latch.awaitExpected();
	}
}
