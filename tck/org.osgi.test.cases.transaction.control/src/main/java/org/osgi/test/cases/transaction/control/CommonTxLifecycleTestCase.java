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
import static org.osgi.service.transaction.control.TransactionStatus.ACTIVE;
import static org.osgi.service.transaction.control.TransactionStatus.COMMITTED;
import static org.osgi.service.transaction.control.TransactionStatus.MARKED_ROLLBACK;
import static org.osgi.service.transaction.control.TransactionStatus.NO_TRANSACTION;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLED_BACK;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.osgi.service.transaction.control.ScopedWorkException;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionException;

/**
 * This test case runs against both local and XA Transaction Control services
 * testing the Scope lifecycle as defined in 147.3.1 and 147.3.3
 * 
 * @author $Id$
 */
public class CommonTxLifecycleTestCase extends TxControlTestCase {
	
	/**
	 * 147.3.1 Required starts a Transaction scope from unscoped
	 */
	@Test
	public void testRequiredFromUnscoped() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();

				TransactionContext current = txControl.getCurrentContext();
				assertThat(current).isNotNull();
				assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
				return null;
			}
		});
	}

	/**
	 * 147.3.1 Required starts a Transaction scope from no transaction
	 */
	@Test
	public void testRequiredFromNoTransaction() {
		
		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();
		
		txControl.notSupported(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				assertThat(outer.getTransactionKey()).isNull();

				TransactionContext nested = txControl
						.required(new Callable<TransactionContext>() {
							@Override
							public TransactionContext call() throws Exception {
								assertThat(txControl.activeScope()).isTrue();
								assertThat(txControl.activeTransaction()).isTrue();

								TransactionContext current = txControl
										.getCurrentContext();
								assertThat(current).isNotNull();
								assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);

								assertThat(current.getTransactionKey()).isNotEqualTo(outer.getTransactionKey());

								return current;
							}
				});

				assertThat(nested.getTransactionStatus()).isEqualTo(COMMITTED);

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);

				return null;
			}
		});
	}

	/**
	 * 147.3.1 Required inherits a Transaction scope from transaction scope
	 */
	@Test
	public void testRequiredFromTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				txControl.required(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isTrue();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);

						assertThat(current.getTransactionKey()).isEqualTo(outer.getTransactionKey());

						return null;
					}
				});

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				return null;
			}
		});
	}

	/**
	 * 147.3.1 RequiresNew starts a Transaction scope from unscoped
	 */
	@Test
	public void testRequiresNewFromUnscoped() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.requiresNew(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();

				TransactionContext current = txControl.getCurrentContext();
				assertThat(current).isNotNull();
				assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
				return null;
			}
		});
	}

	/**
	 * 147.3.1 RequiresNew starts a Transaction scope from no transaction
	 */
	@Test
	public void testRequiresNewFromNoTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.notSupported(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				assertThat(outer.getTransactionKey()).isNull();

				TransactionContext nested = txControl
						.requiresNew(new Callable<TransactionContext>() {
					@Override
							public TransactionContext call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isTrue();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);

						assertThat(current.getTransactionKey()).isNotEqualTo(outer.getTransactionKey());

								return current;
					}
				});

				assertThat(nested.getTransactionStatus()).isEqualTo(COMMITTED);

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);

				return null;
			}
		});
	}

	/**
	 * 147.3.1 RequiresNew inherits a Transaction scope from transaction scope
	 */
	@Test
	public void testRequiresNewFromTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				TransactionContext nested = txControl
						.requiresNew(new Callable<TransactionContext>() {
					@Override
							public TransactionContext call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isTrue();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);

						assertThat(current.getTransactionKey()).isNotEqualTo(outer.getTransactionKey());

								return current;
					}
				});

				assertThat(nested.getTransactionStatus()).isEqualTo(COMMITTED);

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				return null;
			}
		});
	}

	/**
	 * 147.3.1 Supports starts a new no transaction scope from unscoped
	 */
	@Test
	public void testSupportsFromUnscoped() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.supports(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();

				TransactionContext current = txControl.getCurrentContext();
				assertThat(current).isNotNull();
				assertThat(current.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				return null;
			}
		});
	}

	/**
	 * 147.3.1 Supports starts a Transaction scope from no transaction
	 */
	@Test
	public void testSupportsFromNoTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.notSupported(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				assertThat(outer.getTransactionKey()).isNull();

				return txControl.supports(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isFalse();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(NO_TRANSACTION);

						assertThat(current.getTransactionKey()).isEqualTo(outer.getTransactionKey());

						return null;
					}
				});
			}
		});
	}

	/**
	 * 147.3.1 Supports inherits a Transaction scope from transaction scope
	 */
	@Test
	public void testSupportsFromTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				txControl.supports(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isTrue();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);

						assertThat(current.getTransactionKey()).isEqualTo(outer.getTransactionKey());

						return null;
					}
				});

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				return null;
			}
		});
	}

	/**
	 * 147.3.1 notSupported starts a new no transaction scope from unscoped
	 */
	@Test
	public void testNotSupportedFromUnscoped() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.notSupported(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();

				TransactionContext current = txControl.getCurrentContext();
				assertThat(current).isNotNull();
				assertThat(current.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				return null;
			}
		});
	}

	/**
	 * 147.3.1 notSupported inherits from no transaction
	 */
	@Test
	public void testNotSupportedFromNoTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.notSupported(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isFalse();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(NO_TRANSACTION);
				assertThat(outer.getTransactionKey()).isNull();

				return txControl.notSupported(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isFalse();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(NO_TRANSACTION);

						assertThat(current.getTransactionKey()).isEqualTo(outer.getTransactionKey());

						return null;
					}
				});
			}
		});
	}

	/**
	 * 147.3.1 NotSupported starts a no transaction scope from transaction scope
	 */
	@Test
	public void testNotSupportedFromTransaction() {

		assertThat(txControl.activeScope()).isFalse();
		assertThat(txControl.activeTransaction()).isFalse();
		assertThat(txControl.getCurrentContext()).isNull();

		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {

				assertThat(txControl.activeScope()).isTrue();
				assertThat(txControl.activeTransaction()).isTrue();
				final TransactionContext outer = txControl.getCurrentContext();
				assertThat(outer).isNotNull();
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				txControl.notSupported(new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						assertThat(txControl.activeScope()).isTrue();
						assertThat(txControl.activeTransaction()).isFalse();

						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();
						assertThat(current.getTransactionStatus()).isEqualTo(NO_TRANSACTION);

						assertThat(current.getTransactionKey()).isNotEqualTo(outer.getTransactionKey());

						return null;
					}
				});

				assertThat(txControl.getCurrentContext()).isSameAs(outer);
				assertThat(outer.getTransactionStatus()).isEqualTo(ACTIVE);

				return null;
			}
		});
	}

	/**
	 * 147.3.3.1 setRollbackOnly test
	 */
	@Test
	public void testMarkRollback() {

		TransactionContext ctx = txControl
				.required(new Callable<TransactionContext>() {
					@Override
					public TransactionContext call() throws Exception {
						TransactionContext current = txControl
								.getCurrentContext();
						assertThat(current).isNotNull();

						assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
						assertThat(current.getRollbackOnly()).isFalse();

						current.setRollbackOnly();

						assertThat(current.getTransactionStatus()).isEqualTo(MARKED_ROLLBACK);
						assertThat(current.getRollbackOnly()).isTrue();

						return current;
					}
				});

		assertThat(ctx.getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.1 runtime exception triggers rollback
	 */
	@Test
	public void testUncheckedExceptionCausesRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.required(new Callable<TransactionContext>() {
				@Override
				public TransactionContext call() throws Exception {
					TransactionContext current = txControl.getCurrentContext();
					assertThat(current).isNotNull();

					assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
					assertThat(current.getRollbackOnly()).isFalse();

					ctx.set(current);

					throw new RuntimeException("Boom");
				}
			});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.1 checked exception triggers rollback
	 */
	@Test
	public void testCheckedExceptionCausesRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.required(new Callable<TransactionContext>() {
				@Override
				public TransactionContext call() throws Exception {
					TransactionContext current = txControl.getCurrentContext();
					assertThat(current).isNotNull();

					assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
					assertThat(current.getRollbackOnly()).isFalse();

					ctx.set(current);

					throw new Exception("Boom");
				}
			});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.2 ignored runtime exception does not trigger rollback
	 */
	@Test
	public void testIgnoredUncheckedExceptionNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build().noRollbackFor(RuntimeException.class).required(
					new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new RuntimeException("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.2 ignored checked exception does not trigger rollback
	 */
	@Test
	public void testIgnoredCheckedExceptionNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build().noRollbackFor(Exception.class).required(
					new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new Exception("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.2 ignored runtime exception does not trigger rollback
	 */
	@Test
	public void testIgnoredUncheckedExceptionSubclassNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build().noRollbackFor(RuntimeException.class).required(
					new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new IllegalArgumentException("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.2 ignored checked exception does not trigger rollback
	 */
	@Test
	public void testIgnoredCheckedExceptionSubclassNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build().noRollbackFor(Exception.class).required(
					new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new ClassNotFoundException("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.2 ignored different runtime exception triggers rollback
	 */
	@Test
	public void testIgnoredDifferentUncheckedExceptionRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build()
					.noRollbackFor(IllegalArgumentException.class)
					.required(new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new RuntimeException("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.2 ignored different checked exception triggers rollback
	 */
	@Test
	public void testIgnoredDifferentCheckedExceptionRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.build()
					.noRollbackFor(ClassNotFoundException.class)
					.required(new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new Exception("Boom");
						}
					});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.2 ignored runtime exception does not trigger rollback
	 */
	@Test
	public void testIgnoredExceptionInstanceNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		try {
			txControl.required(new Callable<TransactionContext>() {
				@Override
				public TransactionContext call() throws Exception {
					TransactionContext current = txControl.getCurrentContext();
					assertThat(current).isNotNull();

					assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
					assertThat(current.getRollbackOnly()).isFalse();

					ctx.set(current);

					RuntimeException runtimeException = new RuntimeException(
							"Boom");

					txControl.ignoreException(runtimeException);

					throw runtimeException;
				}
			});
			fail("Expected a ScopedWorkException");
		} catch (ScopedWorkException e) {}

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.3 runtime exception triggers rollback in inherited transaction
	 */
	@Test
	public void testUncheckedExceptionMarksRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		txControl.required(new Callable<TransactionContext>() {
			@Override
			public TransactionContext call() throws Exception {
				try {
					txControl.required(new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new RuntimeException("Boom");
						}
					});
					fail("Expected a ScopedWorkException");
				} catch (ScopedWorkException e) {}

				assertThat(ctx.get().getTransactionStatus()).isEqualTo(MARKED_ROLLBACK);
				return null;
			}
		});

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.3 checked exception triggers rollback in inherited transaction
	 */
	@Test
	public void testCheckedExceptionMarksRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		txControl.required(new Callable<TransactionContext>() {
			@Override
			public TransactionContext call() throws Exception {
				try {
					txControl.required(new Callable<TransactionContext>() {
						@Override
						public TransactionContext call() throws Exception {
							TransactionContext current = txControl
									.getCurrentContext();
							assertThat(current).isNotNull();

							assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
							assertThat(current.getRollbackOnly()).isFalse();

							ctx.set(current);

							throw new Exception("Boom");
						}
					});
					fail("Expected a ScopedWorkException");
				} catch (ScopedWorkException e) {}

				assertThat(ctx.get().getTransactionStatus()).isEqualTo(MARKED_ROLLBACK);
				return null;
			}
		});

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(ROLLED_BACK);
	}

	/**
	 * 147.3.3.3 runtime exception triggers rollback in inherited transaction
	 */
	@Test
	public void testIgnoredNestedUncheckedExceptionNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		txControl.required(new Callable<TransactionContext>() {
			@Override
			public TransactionContext call() throws Exception {
				try {
					txControl.build()
							.noRollbackFor(RuntimeException.class)
							.required(new Callable<TransactionContext>() {
								@Override
								public TransactionContext call()
										throws Exception {
									TransactionContext current = txControl
											.getCurrentContext();
									assertThat(current).isNotNull();

									assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
									assertThat(current.getRollbackOnly()).isFalse();

									ctx.set(current);

									throw new RuntimeException("Boom");
								}
							});
					fail("Expected a ScopedWorkException");
				} catch (ScopedWorkException e) {}

				assertThat(ctx.get().getTransactionStatus()).isEqualTo(ACTIVE);
				return null;
			}
		});

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * 147.3.3.3 checked exception triggers rollback in inherited transaction
	 */
	@Test
	public void testIgnoredNestedCheckedExceptionNoRollback() {

		final AtomicReference<TransactionContext> ctx = new AtomicReference<>();

		txControl.required(new Callable<TransactionContext>() {
			@Override
			public TransactionContext call() throws Exception {
				try {
					txControl.build().noRollbackFor(Exception.class).required(
							new Callable<TransactionContext>() {
								@Override
								public TransactionContext call()
										throws Exception {
									TransactionContext current = txControl
											.getCurrentContext();
									assertThat(current).isNotNull();

									assertThat(current.getTransactionStatus()).isEqualTo(ACTIVE);
									assertThat(current.getRollbackOnly()).isFalse();

									ctx.set(current);

									throw new Exception("Boom");
								}
							});
					fail("Expected a ScopedWorkException");
				} catch (ScopedWorkException e) {}

				assertThat(ctx.get().getTransactionStatus()).isEqualTo(ACTIVE);
				return null;
			}
		});

		assertThat(ctx.get().getTransactionStatus()).isEqualTo(COMMITTED);
	}

	/**
	 * Note that read only support is not required so this test must have a bail
	 * out! 147.3.3.4.3 Read Only status cannot be changed in inherited
	 * transaction
	 */
	@Test
	public void testReadOnlyChange() {

		txControl.build().readOnly().required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (txControl.getCurrentContext().isReadOnly()) {
					// Supported, so run the test

					try {
						txControl.required(new Callable<Object>() {
							@Override
							public Object call() throws Exception {
								fail("Should not start the nested transaction");
								return null;
							}
						});
						fail("Should not be able to expand the read only scope");
					} catch (TransactionException te) {}
				}
				return null;
			}
		});

	}

	/**
	 * Note that read only support is not required so this test must have a bail
	 * out! 147.3.3.4.3 Read Only status cannot be changed in inherited
	 * transaction
	 */
	@Test
	public void testReadOnlySame() {

		txControl.build().readOnly().required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (txControl.getCurrentContext().isReadOnly()) {
					// Supported, so run the test
					return txControl.build().readOnly().required(
							new Callable<Object>() {
								@Override
								public Object call() throws Exception {
									return null;
								}
							});
				}
				return null;
			}
		});

	}

	/**
	 * Note that read only support is not required so this test must have a bail
	 * out! 147.3.3.4.3 Read Only status cannot be changed in inherited
	 * transaction
	 */
	@Test
	public void testReadOnlyChangeNewTran() {

		txControl.build().readOnly().required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (txControl.getCurrentContext().isReadOnly()) {
					// Supported, so run the test
					txControl.requiresNew(new Callable<Object>() {
						@Override
						public Object call() throws Exception {
							return null;
						}
					});
				}
				return null;
			}
		});

	}
}
