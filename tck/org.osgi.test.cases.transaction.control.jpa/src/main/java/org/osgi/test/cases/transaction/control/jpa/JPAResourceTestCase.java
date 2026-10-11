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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.util.tracker.ServiceTracker;

public abstract class JPAResourceTestCase {

	@InjectBundleContext
	BundleContext		context;
	
	private ServiceTracker<JPAEntityManagerProviderFactory,JPAEntityManagerProviderFactory>	tracker;

	private ServiceTracker<DataSourceFactory,DataSourceFactory>							dsfTracker;

	private ServiceTracker<EntityManagerFactoryBuilder,EntityManagerFactoryBuilder>			emfBuilderTracker;

	protected JPAEntityManagerProviderFactory												jpaResourceProviderFactory;

	protected DataSourceFactory															dataSourceFactory;

	protected EntityManagerFactoryBuilder													emfBuilder;

	protected Bundle																		jpaResourceProviderBundle;

	protected boolean												localEnabled;

	protected boolean												xaEnabled;

	protected boolean																		recoveryEnabled;

	@BeforeEach
	public void setUp() throws Exception {
		tracker = new ServiceTracker<>(context,
				JPAEntityManagerProviderFactory.class, null);
		tracker.open();

		dsfTracker = new ServiceTracker<>(context, DataSourceFactory.class,
				null);
		dsfTracker.open();

		emfBuilderTracker = new ServiceTracker<>(context,
				EntityManagerFactoryBuilder.class, null);
		emfBuilderTracker.open();

		jpaResourceProviderFactory = tracker.waitForService(5000);
		dataSourceFactory = dsfTracker.waitForService(5000);
		emfBuilder = emfBuilderTracker.waitForService(5000);

		assertThat(jpaResourceProviderFactory).as("No Tx Control service available within 5 seconds").isNotNull();

		ServiceReference<JPAEntityManagerProviderFactory> ref = tracker
				.getServiceReference();

		jpaResourceProviderBundle = ref.getBundle();

		localEnabled = toBoolean(ref.getProperty("osgi.local.enabled"));
		xaEnabled = toBoolean(ref.getProperty("osgi.xa.enabled"));

		assertThat(localEnabled || xaEnabled).as("This transaction control service does not support local or xa transactions").isTrue();

		recoveryEnabled = toBoolean(ref.getProperty("osgi.recovery.enabled"));
	}
	
	@AfterEach
	public void tearDown() {
		tracker.close();
		dsfTracker.close();
		emfBuilderTracker.close();
	}
	
	protected boolean toBoolean(Object o) {
		return o == null ? false : Boolean.parseBoolean(o.toString());
	}
}
