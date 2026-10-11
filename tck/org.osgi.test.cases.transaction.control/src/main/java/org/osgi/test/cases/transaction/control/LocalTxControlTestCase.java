/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/
package org.osgi.test.cases.transaction.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.osgi.service.transaction.control.TransactionStatus.COMMITTING;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLING_BACK;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.osgi.service.transaction.control.ScopedWorkException;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.TransactionRolledBackException;
import org.osgi.test.cases.transaction.control.resources.RecordingLocalResource;

/**
 * This test case runs against local Transaction Control services (i.e. ones
 * that aren't XA) testing the transaction lifecycle as defined in 147.4.5
 * 
 * @author $Id$
 */
public class LocalTxControlTestCase extends TxControlTestCase {
	
	/**
	 * A basic test that ensures that a Local Transaction Control reports that
	 * it supports local resources
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlSupportsLocal() throws Exception {

		if (xaEnabled) {
			return;
		}

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertThat(txControl.getCurrentContext().supportsLocal()).isTrue();
				return null;
			}
		});
	}

	/**
	 * A commit with a single local resource
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlLocalCommit() throws Exception {

		if (xaEnabled) {
			return;
		}

		final RecordingLocalResource resource = new RecordingLocalResource(
				txControl);

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				txControl.getCurrentContext()
						.registerLocalResource(resource);
				return null;
			}
		});

		assertThat(resource.getRollbackStatus()).isNull();
		assertThat(resource.getCommitStatus()).isEqualTo(COMMITTING);
	}

	/**
	 * A rollback with a single local resource
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlLocalRollback() throws Exception {

		if (xaEnabled) {
			return;
		}

		final RecordingLocalResource resource = new RecordingLocalResource(
				txControl);

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				txControl.getCurrentContext().registerLocalResource(resource);

				txControl.getCurrentContext().setRollbackOnly();
				return null;
			}
		});

		assertThat(resource.getCommitStatus()).isNull();
		assertThat(resource.getRollbackStatus()).isEqualTo(ROLLING_BACK);
	}

	/**
	 * A commit with a failing first local resource
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlLocalFirstParticipantFailure() throws Exception {

		if (xaEnabled) {
			return;
		}

		final IllegalStateException ise = new IllegalStateException("Bang");

		final RecordingLocalResource resource = new RecordingLocalResource(
				txControl) {
			@Override
			public void commit() throws TransactionException {
				super.commit();
				throw ise;
			}
		};
		final RecordingLocalResource resource2 = new RecordingLocalResource(
				txControl) {
			@Override
			public void commit() throws TransactionException {
				super.commit();
				throw ise;
			}
		};

		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					txControl.getCurrentContext()
							.registerLocalResource(resource);
					txControl.getCurrentContext()
							.registerLocalResource(resource2);
					return null;
				}
			});
			fail("Must throw a TransactionRolledBackException");
		} catch (TransactionRolledBackException te) {
			assertThat(te.getCause()).isSameAs(ise);
		}

		if (resource.getCommitStatus() == null) {
			assertThat(resource.getRollbackStatus()).isEqualTo(ROLLING_BACK);
			assertThat(resource2.getRollbackStatus()).isNull();
			assertThat(resource2.getCommitStatus()).isEqualTo(COMMITTING);
		} else {
			assertThat(resource.getRollbackStatus()).isNull();
			assertThat(resource2.getRollbackStatus()).isEqualTo(ROLLING_BACK);
			assertThat(resource.getCommitStatus()).isEqualTo(COMMITTING);
			assertThat(resource2.getCommitStatus()).isNull();
		}
	}

	/**
	 * A commit with a failing second local resource
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlLocalSecondParticipantFailure() throws Exception {

		if (xaEnabled) {
			return;
		}

		final IllegalStateException ise = new IllegalStateException("Bang");

		final AtomicBoolean fail = new AtomicBoolean(false);

		final RecordingLocalResource resource = new RecordingLocalResource(
				txControl) {
			@Override
			public void commit() throws TransactionException {
				super.commit();
				if (fail.getAndSet(!fail.get())) {
					throw ise;
				}
			}
		};
		final RecordingLocalResource resource2 = new RecordingLocalResource(
				txControl) {
			@Override
			public void commit() throws TransactionException {
				super.commit();
				if (fail.getAndSet(!fail.get())) {
					throw ise;
				}
			}
		};
		final RecordingLocalResource resource3 = new RecordingLocalResource(
				txControl) {
			@Override
			public void commit() throws TransactionException {
				super.commit();
				if (fail.getAndSet(!fail.get())) {
					throw ise;
				}
			}
		};

		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					txControl.getCurrentContext()
							.registerLocalResource(resource);
					txControl.getCurrentContext()
							.registerLocalResource(resource2);
					txControl.getCurrentContext()
							.registerLocalResource(resource3);
					return null;
				}
			});
			fail("Must throw a TransactionException");
		} catch (TransactionException te) {
			assertThat(te.getCause()).isSameAs(ise);
		}

		assertThat(resource.getRollbackStatus()).isNull();
		assertThat(resource.getCommitStatus()).isEqualTo(COMMITTING);
		assertThat(resource2.getRollbackStatus()).isNull();
		assertThat(resource2.getCommitStatus()).isEqualTo(COMMITTING);
		assertThat(resource3.getRollbackStatus()).isNull();
		assertThat(resource3.getCommitStatus()).isEqualTo(COMMITTING);
	}

	/**
	 * A rollback with a failing first local resource
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTxControlLocalFirstParticipantRollbackFailure()
			throws Exception {

		if (xaEnabled) {
			return;
		}

		final IllegalStateException ise = new IllegalStateException("Bang");
		final IllegalStateException ise2 = new IllegalStateException("I Win!");

		final RecordingLocalResource resource = new RecordingLocalResource(
				txControl) {
			@Override
			public void rollback() throws TransactionException {
				super.rollback();
				throw ise;
			}
		};
		final RecordingLocalResource resource2 = new RecordingLocalResource(
				txControl) {
			@Override
			public void rollback() throws TransactionException {
				super.rollback();
				throw ise;
			}
		};

		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					txControl.getCurrentContext()
							.registerLocalResource(resource);
					txControl.getCurrentContext()
							.registerLocalResource(resource2);

					throw ise2;
				}
			});
			fail("Must throw a TransactionRolledBackException");
		} catch (ScopedWorkException swe) {
			assertThat(swe.getCause()).isSameAs(ise2);
			Throwable[] suppressed = swe.getSuppressed();
			assertThat(suppressed).isNotNull();
			assertThat(suppressed).hasSize(2);
			assertThat(suppressed[0]).isSameAs(ise);
			assertThat(suppressed[1]).isSameAs(ise);
		}

		assertThat(resource.getCommitStatus()).isNull();
		assertThat(resource2.getCommitStatus()).isNull();
		assertThat(resource.getRollbackStatus()).isEqualTo(ROLLING_BACK);
		assertThat(resource2.getRollbackStatus()).isEqualTo(ROLLING_BACK);
	}
}
