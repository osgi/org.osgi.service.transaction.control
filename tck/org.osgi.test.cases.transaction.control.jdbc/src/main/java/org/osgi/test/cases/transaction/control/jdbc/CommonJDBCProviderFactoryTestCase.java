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
package org.osgi.test.cases.transaction.control.jdbc;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.osgi.service.transaction.control.jdbc.JDBCConnectionProviderFactory.CONNECTION_POOLING_ENABLED;
import static org.osgi.service.transaction.control.jdbc.JDBCConnectionProviderFactory.MAX_CONNECTIONS;
import static org.osgi.service.transaction.control.jdbc.JDBCConnectionProviderFactory.MIN_CONNECTIONS;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.jdbc.JDBCConnectionProvider;
import org.osgi.test.cases.transaction.control.jdbc.control.NoTransactionTxControl;
import org.osgi.test.cases.transaction.control.jdbc.control.TestTransactionControl;
import org.osgi.test.cases.transaction.control.jdbc.control.TransactionTxControl;
import org.osgi.test.cases.transaction.control.jdbc.control.UnscopedTxControl;

/**
 * This test covers common ResourceProvider requirements as defined in 147.5.1
 * and 147.5.2 - it is extended to cover the various ways of making a provider.
 */
public abstract class CommonJDBCProviderFactoryTestCase
		extends JDBCResourceTestCase {
	
	protected static final String	H2_URL	= "jdbc:h2:mem:test";

	private Connection				unmanaged;

	private JDBCConnectionProvider	provider;

	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();

		provider = createProvider(0);

		unmanaged = dataSourceFactory.createDriver(null).connect(H2_URL, null);
		unmanaged.setAutoCommit(false);
	}

	private JDBCConnectionProvider createProvider(int poolSize)
			throws SQLException {
		Properties props = new Properties();
		props.setProperty("url", H2_URL);

		Map<String,Object> providerConfig = new HashMap<>();
		if (poolSize == 0) {
			providerConfig.put(CONNECTION_POOLING_ENABLED, false);
		} else {
			providerConfig.put(MIN_CONNECTIONS, poolSize);
			providerConfig.put(MAX_CONNECTIONS, poolSize);
		}

		return getProvider(props, providerConfig);
	}

	protected abstract JDBCConnectionProvider getProvider(Properties props,
			Map<String,Object> providerConfig) throws SQLException;

	@AfterEach
	public void tearDown() {

		try (Connection c = unmanaged) {
			try (Statement s = unmanaged.createStatement()) {
				s.execute("DROP TABLE TEST");
				unmanaged.commit();
			}
		} catch (Exception e) {}

		closeProvider();

		super.tearDown();
	}

	private void closeProvider() {
		try {
			jdbcResourceProviderFactory.releaseProvider(provider);
		} catch (Exception e) {}
	}

	/**
	 * 147.5.1.1 Connection must be enlisted in the ongoing transaction
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEnlistmentLocal() throws Exception {

		if (!localEnabled) {
			return;
		}

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TransactionTxControl(true, false);

		Connection scoped = provider.getResource(tran);

		ResultSet rs = scoped.createStatement()
				.executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		assertThat(tran.getEnlistedLocalResources()).hasSize(1);

		tran.finish(false);
	}

	/**
	 * 147.5.1.1 Connection must be enlisted in the ongoing transaction
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEnlistmentXA() throws Exception {

		if (!xaEnabled) {
			return;
		}

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TransactionTxControl(false, true);

		Connection scoped = provider.getResource(tran);

		ResultSet rs = scoped.createStatement()
				.executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		assertThat(tran.getEnlistedXAResources()).hasSize(1);

		tran.finish(false);
	}

	/**
	 * 147.5.1.1 Connection must be released at the end of the scope even if not
	 * explicitly closed
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNoNeedToClose() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new NoTransactionTxControl();

		Connection scoped = provider.getResource(noTran);

		for (int i = 0; i < 20; i++) {
			ResultSet rs = scoped.createStatement()
					.executeQuery("SELECT MESSAGE FROM TEST");
			rs.next();
			assertThat(rs.getString(1)).isEqualTo("TEST");
			noTran.finish(false);
		}

		try (Statement s = unmanaged.createStatement()) {
			try (ResultSet rs = s.executeQuery(
					"Select COUNT(ID) from INFORMATION_SCHEMA.SESSIONS")) {
				rs.next();
				assertThat(rs.getInt(1)).isOne();
			}
		}
	}

	/**
	 * 147.5.1.2 Unscoped usage must throw an exception
	 * 
	 * @throws Exception
	 */
	@Test
	public void testUnscopedUsage() throws Exception {

		TransactionControl unscoped = new UnscopedTxControl();

		Connection scoped = provider.getResource(unscoped);

		assertThatExceptionOfType(TransactionException.class).as("Should not be able to use a scoped resource outside of a scope")
				.isThrownBy(() -> scoped.createStatement());
	}

	/**
	 * 147.5.1.3 Calls to close must be ignored
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCloseIgnored() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new NoTransactionTxControl();

		Connection scoped = provider.getResource(noTran);

		ResultSet rs = scoped.createStatement()
				.executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		// This should be ignored
		scoped.close();

		rs = scoped.createStatement().executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		noTran.finish(false);
	}

	/**
	 * 147.5.1.3 & 147.5.2.1 Calls to:
	 * <ul>
	 * <li>{@link Connection#commit()}
	 * <li>{@link Connection#setAutoCommit(boolean)}
	 * <li>{@link Connection#setSavepoint()}
	 * <li>{@link Connection#setSavepoint(String)}
	 * <li>{@link Connection#releaseSavepoint()}
	 * <li>{@link Connection#rollback()}
	 * <li>{@link Connection#rollback(Savepoint)}
	 * </ul>
	 * are errors in a Transactional scope
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCommitAndRollbackIgnored() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TransactionTxControl(localEnabled,
				xaEnabled);

		Connection scoped = provider.getResource(tran);

		assertThatExceptionOfType(TransactionException.class).as("commit Should not be callable")
				.isThrownBy(() -> scoped.commit());
		assertThatExceptionOfType(TransactionException.class).as("setAutoCommit Should not be callable")
				.isThrownBy(() -> scoped.setAutoCommit(true));
		assertThatExceptionOfType(TransactionException.class).as("setSavepoint Should not be callable")
				.isThrownBy(() -> scoped.setSavepoint());
		assertThatExceptionOfType(TransactionException.class).as("setSavepoint Should not be callable")
				.isThrownBy(() -> scoped.setSavepoint("test"));
		assertThatExceptionOfType(TransactionException.class).as("releaseSavepoint Should not be callable")
				.isThrownBy(() -> scoped.releaseSavepoint(null));
		assertThatExceptionOfType(TransactionException.class).as("rollback Should not be callable")
				.isThrownBy(() -> scoped.rollback());
		assertThatExceptionOfType(TransactionException.class).as("rollback Should not be callable")
				.isThrownBy(() -> scoped.rollback(null));

		tran.finish(false);
	}

	/**
	 * 147.5.1.4 Releasing a resource provider must close its resources
	 * 
	 * @throws Exception
	 */
	@Test
	public void testProviderRelease() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new NoTransactionTxControl();

		Connection scoped = provider.getResource(noTran);

		jdbcResourceProviderFactory.releaseProvider(provider);

		try {
			scoped.createStatement();
			fail("Should not be able to use a scoped resource outside of a scope");
		} catch (TransactionException te) {
			// Expected
		}

		noTran.finish(false);

		// Create a pooled provider

		provider = createProvider(5);

		scoped = provider.getResource(noTran);
		ResultSet rs = scoped.createStatement()
				.executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");
		noTran.finish(false);

		// Give the pool time to settle
		Thread.sleep(1000);

		try (Statement s = unmanaged.createStatement()) {
			try (ResultSet rs2 = s.executeQuery(
					"Select COUNT(*) from INFORMATION_SCHEMA.SESSIONS")) {
				rs2.next();
				// 5 in the pool and one unmanaged
				assertThat(rs2.getInt(1)).isEqualTo(6);
			}
		}

		jdbcResourceProviderFactory.releaseProvider(provider);

		try (Statement s = unmanaged.createStatement()) {
			try (ResultSet rs2 = s.executeQuery(
					"Select COUNT(*) from INFORMATION_SCHEMA.SESSIONS")) {
				rs2.next();
				// The pool is closed and one unmanaged
				assertThat(rs2.getInt(1)).isOne();
			}
		}

	}

	/**
	 * 147.5.2.1 transaction commit isolation
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTransactionIsolation() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TransactionTxControl(localEnabled, xaEnabled);

		Connection scoped = provider.getResource(tran);

		Statement scopedStatement = scoped.createStatement();
		scopedStatement.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");

		assertThat(getMessages()).isEqualTo(emptyList());

		tran.finish(false);

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		scopedStatement = scoped.createStatement();
		scopedStatement.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST2')");

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));
		tran.setRollbackOnly();

		tran.finish(false);

		assertThat(getMessages()).isEqualTo(asList("TEST"));
	}

	private List<String> getMessages() throws SQLException {
		List<String> messages = new ArrayList<>();
		try (Statement s = unmanaged.createStatement()) {
			try (ResultSet rs = s.executeQuery("Select MESSAGE from TEST")) {
				while (rs.next()) {
					messages.add(rs.getString(1));
				}
			}
		}
		return messages;
	}

	/**
	 * 147.5.2.2 no transaction commit isolation
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNoTransactionIsolation() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new NoTransactionTxControl();

		Connection scoped = provider.getResource(noTran);

		scoped.setAutoCommit(false);
		Statement scopedStatement = scoped.createStatement();
		scopedStatement.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");

		assertThat(getMessages()).isEqualTo(emptyList());

		scoped.commit();

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		scopedStatement.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST2')");

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		Savepoint sp = scoped.setSavepoint("testing");

		scopedStatement.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST3')");

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		scoped.rollback(sp);
		scoped.commit();

		assertThat(getMessages()).isEqualTo(asList("TEST", "TEST2"));

		noTran.finish(false);
	}

	/**
	 * 147.5.2.3 Calls to abort must be ignored
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAbortIgnored() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new NoTransactionTxControl();

		Connection scoped = provider.getResource(noTran);

		ResultSet rs = scoped.createStatement()
				.executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		// This should be ignored
		scoped.abort(Executors.newSingleThreadExecutor());

		rs = scoped.createStatement().executeQuery("SELECT MESSAGE FROM TEST");
		rs.next();
		assertThat(rs.getString(1)).isEqualTo("TEST");

		noTran.finish(false);
	}
}
