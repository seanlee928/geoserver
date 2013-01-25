/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.locks.LockProvider;

/**
 * A lock provider that delegates the work to another {@link LockProvider} instance, which needs to
 * be configured by calling {@link #setDelegate(LockProvider)}. A un-configured instance will throw
 * {@link NullPointerException} when {@link #getLock(String)} is called.
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class ConfigurableLockProvider implements LockProvider {

    LockProvider delegate;

    @Override
    public Lock getLock(String lockKey) throws GeoWebCacheException {

    	int x = 0;
    	return delegate.getLock(lockKey);
    }

    public LockProvider getDelegate() {
        return delegate;
    }

    public void setDelegate(LockProvider delegate) {
        this.delegate = delegate;
    }

}
