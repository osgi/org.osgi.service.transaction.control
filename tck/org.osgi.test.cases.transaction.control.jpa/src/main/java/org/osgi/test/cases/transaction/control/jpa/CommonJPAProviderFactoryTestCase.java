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
package org.osgi.test.cases.transaction.control.jpa;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaQuery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;
import org.osgi.test.cases.transaction.control.jpa.control.TestTransactionControl;
import org.osgi.test.cases.transaction.control.jpa.control.TestTxControlImpl;
import org.osgi.test.cases.transaction.control.jpa.entity.TestEntity;

/**
 * This test covers common ResourceProvider requirements as defined in 147.5.1
 * and 147.5.2 - it is extended to cover the various ways of making a provider.
 */
public abstract class CommonJPAProviderFactoryTestCase
		extends JPAResourceTestCase {
	
	protected static final String	H2_URL	= "jdbc:h2:mem:test";

	protected Connection				unmanaged;

	protected JPAEntityManagerProvider	provider;

	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();

		provider = createProvider();

		unmanaged = dataSourceFactory.createDriver(null).connect(H2_URL, null);
		unmanaged.setAutoCommit(false);
	}

	protected abstract JPAEntityManagerProvider createProvider(int poolSize)
			throws Exception;

	protected JPAEntityManagerProvider createProvider() throws Exception {
		return createProvider(0);
	}

	@AfterEach
	public void tearDown() {

		try (Connection c = unmanaged) {
			try (Statement s = unmanaged.createStatement()) {
				s.execute("DROP TABLE TEST");
				unmanaged.commit();
			}
		} catch (Exception e) {}

		safeCloseProvider();

		super.tearDown();
	}

	private void safeCloseProvider() {
		try {
			releaseProvider();
		} catch (Exception e) {}
	}

	protected void releaseProvider() {
		jpaResourceProviderFactory.releaseProvider(provider);
	}

	/**
	 * 147.5.1.1 EntityManager must be enlisted in the ongoing transaction
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

		TestTransactionControl tran = new TestTxControlImpl(true, false, true);

		EntityManager scoped = provider.getResource(tran);

		CriteriaQuery<TestEntity> query = scoped.getCriteriaBuilder()
				.createQuery(TestEntity.class);
		query.from(TestEntity.class);
		assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");

		assertThat(tran.getEnlistedLocalResources()).hasSize(1);

		tran.finish(false);
	}

	/**
	 * 147.5.1.1 EntityManager must be enlisted in the ongoing transaction
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

		TestTransactionControl tran = new TestTxControlImpl(false, true, true);

		EntityManager scoped = provider.getResource(tran);

		CriteriaQuery<TestEntity> query = scoped.getCriteriaBuilder()
				.createQuery(TestEntity.class);
		query.from(TestEntity.class);
		assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");

		assertThat(tran.getEnlistedXAResources()).hasSize(1);

		tran.finish(false);
	}

	/**
	 * 147.5.1.1 EntityManager must be released at the end of the scope even if
	 * not explicitly closed
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

		TestTransactionControl noTran = new TestTxControlImpl(true, true,
				false);

		EntityManager scoped = provider.getResource(noTran);

		for (int i = 0; i < 20; i++) {

			CriteriaQuery<TestEntity> query = scoped.getCriteriaBuilder()
					.createQuery(TestEntity.class);
			query.from(TestEntity.class);
			assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");
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

		TestTransactionControl noTran = new TestTxControlImpl(true, true,
				false);

		EntityManager scoped = provider.getResource(noTran);

		CriteriaQuery<TestEntity> query = scoped.getCriteriaBuilder()
				.createQuery(TestEntity.class);
		query.from(TestEntity.class);
		assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");

		// This should be ignored
		scoped.close();

		query = scoped.getCriteriaBuilder().createQuery(TestEntity.class);
		query.from(TestEntity.class);
		assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");

		noTran.finish(false);
	}

	/**
	 * 147.5.1.3 & 147.5.3.1 Calls to {@link EntityManager#getTransaction()} are
	 * errors in a Transactional scope, and calls to
	 * {@link EntityManager#joinTransaction()} are no-ops
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetTransactionForbidden() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TestTxControlImpl(localEnabled,
				xaEnabled, true);

		EntityManager scoped = provider.getResource(tran);

		assertThatExceptionOfType(TransactionException.class).as("commit Should not be callable")
				.isThrownBy(() -> scoped.getTransaction());

		scoped.joinTransaction();

		assertThat(scoped.isJoinedToTransaction()).isTrue();

		tran.finish(false);
	}

	/**
	 * 147.5.1.3 & 147.5.3.2 Calls to {@link EntityManager#joinTransaction()}
	 * are errors in no Transaction scope, and calls to
	 * 
	 * @throws Exception
	 */
	@Test
	public void testJoinForbidden() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			s.execute("INSERT INTO TEST (MESSAGE) VALUES ('TEST')");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new TestTxControlImpl(localEnabled,
				xaEnabled,
				false);

		EntityManager scoped = provider.getResource(noTran);

		assertThatExceptionOfType(TransactionException.class).as("joinTransaction Should not be callable")
				.isThrownBy(() -> scoped.joinTransaction());

		assertThat(scoped.isJoinedToTransaction()).isFalse();

		noTran.finish(false);
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

		TestTransactionControl noTran = new TestTxControlImpl(localEnabled,
				xaEnabled,
				false);

		EntityManager scoped = provider.getResource(noTran);

		releaseProvider();

		try {
			scoped.getCriteriaBuilder();
			fail("This resource should be invalid after the provider is released");
		} catch (TransactionException te) {
			// expected
		}

		noTran.finish(false);

		// Create a pooled provider

		provider = createProvider(5);

		scoped = provider.getResource(noTran);
		CriteriaQuery<TestEntity> query = scoped.getCriteriaBuilder()
				.createQuery(TestEntity.class);
		query.from(TestEntity.class);
		assertThat(scoped.createQuery(query).getSingleResult().message).isEqualTo("TEST");
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

		releaseProvider();

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
	 * 147.5.3.1 transaction commit isolation
	 * 
	 * @throws Exception
	 */
	@Test
	public void testTransactionIsolation() throws Exception {

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			unmanaged.commit();
		}

		TestTransactionControl tran = new TestTxControlImpl(localEnabled,
				xaEnabled, true);

		EntityManager scoped = provider.getResource(tran);

		TestEntity te = new TestEntity();
		te.message = "TEST";

		scoped.persist(te);

		assertThat(getMessages()).isEqualTo(emptyList());

		tran.finish(false);

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		te = new TestEntity();
		te.message = "TEST2";
		scoped.persist(te);

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
	 * 147.5.3.2 no transaction commit isolation
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNoTransactionIsolation() throws Exception {

		if (!localEnabled) {
			return;
		}

		try (Statement s = unmanaged.createStatement()) {
			s.execute("CREATE TABLE TEST (MESSAGE varchar(255) NOT NULL)");
			unmanaged.commit();
		}

		TestTransactionControl noTran = new TestTxControlImpl(localEnabled,
				xaEnabled, false);

		EntityManager scoped = provider.getResource(noTran);

		scoped.getTransaction().begin();

		TestEntity te = new TestEntity();
		te.message = "TEST";

		scoped.persist(te);

		assertThat(getMessages()).isEqualTo(emptyList());

		scoped.getTransaction().commit();

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		scoped.getTransaction().begin();

		te = new TestEntity();
		te.message = "TEST2";

		scoped.persist(te);

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		scoped.getTransaction().setRollbackOnly();

		scoped.getTransaction().rollback();

		assertThat(getMessages()).isEqualTo(singletonList("TEST"));

		noTran.finish(false);
	}
}
