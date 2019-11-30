package com.carolinarollergirls.scoreboard.core;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProvider;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.AddRemoveProperty;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.PermanentProperty;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.ValueWithId;
import com.carolinarollergirls.scoreboard.utils.ValWithId;

public interface Clients extends ScoreBoardEventProvider {
    public enum Child implements AddRemoveProperty {
        CLIENT(Client.class),
        DEVICE(Device.class);

        private Child(Class<? extends ValueWithId> t) { type = t; }
        private final Class<? extends ValueWithId> type;
        @Override
        public Class<? extends ValueWithId> getType() { return type; }
    }
    public void postAutosaveUpdate();

    public Device getDevice(String sessionId);
    public Device getOrAddDevice(String sessionId, Object session);
    public int gcOldDevices(long threshold);

    public Client addClient(String deviceId, String remoteAddr, String source, String platform);

    // An active websocket client.
    public static interface Client extends ScoreBoardEventProvider {
        public void write();

        public enum Value implements PermanentProperty {
            ID(String.class, ""),            
            DEVICE(Device.class, null),
            REMOTE_ADDR(String.class, ""),
            PLATFORM(String.class, ""),
            SOURCE(String.class, ""),
            CREATED(Long.class, 0),
            WROTE(Long.class, 0);

            private Value(Class<?> t, Object dv) { type = t; defaultValue = dv; }
            private final Class<?> type;
            private final Object defaultValue;
            @Override
            public Class<?> getType() { return type; }
            @Override
            public Object getDefaultValue() { return defaultValue; }
        }
    }
 
    // A device is a HTTP cookie.
    public static interface Device extends ScoreBoardEventProvider {
        public String getName();
        public long getCreated();

        public void access();

        // This is in-memory only, as session objects need to be shared across
        // requests so we don't refresh cookies in every single HTTP response.
        public Object getOrAddSession(Object session);
 
        public enum Value implements PermanentProperty {
            ID(String.class, ""),
            SESSION_ID_SECRET(String.class, ""),   // The cookie.
            NAME(String.class, ""),                // A human-readable name.
            REMOTE_ADDR(String.class, ""),
            PLATFORM(String.class, ""),
            COMMENT(String.class, ""),
            CREATED(Long.class, 0),
            WROTE(Long.class, 0),
            ACCESSED(Long.class, 0);

            private Value(Class<?> t, Object dv) { type = t; defaultValue = dv; }
            private final Class<?> type;
            private final Object defaultValue;
            @Override
            public Class<?> getType() { return type; }
            @Override
            public Object getDefaultValue() { return defaultValue; }
        }
        public enum Child implements AddRemoveProperty {
            CLIENT(Client.class);

            private Child(Class<? extends ValueWithId> t) { type = t; }
            private final Class<? extends ValueWithId> type;
            @Override
            public Class<? extends ValueWithId> getType() { return type; }
        }
    }
}