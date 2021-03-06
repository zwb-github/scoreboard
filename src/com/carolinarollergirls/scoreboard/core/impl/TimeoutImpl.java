package com.carolinarollergirls.scoreboard.core.impl;

import java.util.UUID;

import com.carolinarollergirls.scoreboard.core.Clock;
import com.carolinarollergirls.scoreboard.core.Jam;
import com.carolinarollergirls.scoreboard.core.Period;
import com.carolinarollergirls.scoreboard.core.ScoreBoard;
import com.carolinarollergirls.scoreboard.core.Team;
import com.carolinarollergirls.scoreboard.core.Timeout;
import com.carolinarollergirls.scoreboard.core.TimeoutOwner;
import com.carolinarollergirls.scoreboard.event.Command;
import com.carolinarollergirls.scoreboard.event.Value;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProviderImpl;
import com.carolinarollergirls.scoreboard.utils.ScoreBoardClock;

public class TimeoutImpl extends ScoreBoardEventProviderImpl<Timeout> implements Timeout {
    public TimeoutImpl(Period p, String id) {
        super(p, id, Period.TIMEOUT);
        initReferences();
        if (id == "noTimeout") {
            set(RUNNING, false);
            set(READONLY, true);
        }
    }

    public TimeoutImpl(Jam precedingJam) {
        super(precedingJam.getParent(), UUID.randomUUID().toString(), Period.TIMEOUT);
        initReferences();
        set(PRECEDING_JAM, precedingJam);
        set(WALLTIME_START, ScoreBoardClock.getInstance().getCurrentWalltime());
        set(PERIOD_CLOCK_ELAPSED_START, scoreBoard.getClock(Clock.ID_PERIOD).getTimeElapsed());
    }

    private void initReferences() {
        addProperties(OWNER, REVIEW, RETAINED_REVIEW, RUNNING, PRECEDING_JAM, PRECEDING_JAM_NUMBER, DURATION,
                PERIOD_CLOCK_ELAPSED_START, PERIOD_CLOCK_ELAPSED_END, PERIOD_CLOCK_END, WALLTIME_START, WALLTIME_END,
                DELETE);
        set(OWNER, Owners.NONE);
        setInverseReference(PRECEDING_JAM, Jam.TIMEOUTS_AFTER);
        setCopy(PRECEDING_JAM_NUMBER, this, PRECEDING_JAM, Jam.NUMBER, true);
    }

    @Override
    public int compareTo(Timeout other) {
        int result = 0;
        if (get(PRECEDING_JAM) != null && other.get(PRECEDING_JAM) != null) {
            result = get(PRECEDING_JAM).compareTo(other.get(PRECEDING_JAM));
        }
        if (result == 0) { result = (int) (get(WALLTIME_START) - other.get(WALLTIME_START)); }
        return result;
    }

    @Override
    protected void valueChanged(Value<?> prop, Object value, Object last, Source source, Flag flag) {
        if (prop == OWNER) {
            if (last instanceof Team) { ((Team) last).remove(Team.TIME_OUT, this); }
            if (value instanceof Team) { ((Team) value).add(Team.TIME_OUT, this); }
            if (get(PRECEDING_JAM) == scoreBoard.getCurrentPeriod().getCurrentJam()) {
                scoreBoard.set(ScoreBoard.NO_MORE_JAM, scoreBoard.get(ScoreBoard.NO_MORE_JAM), Source.RECALCULATE);
            }
        }
        if (prop == REVIEW && getOwner() instanceof Team) {
            ((Team) getOwner()).recountTimeouts();
            if (get(PRECEDING_JAM) == scoreBoard.getCurrentPeriod().getCurrentJam()) {
                scoreBoard.set(ScoreBoard.NO_MORE_JAM, scoreBoard.get(ScoreBoard.NO_MORE_JAM), Source.RECALCULATE);
            }
        }
        if (prop == RETAINED_REVIEW && getOwner() instanceof Team) { ((Team) getOwner()).recountTimeouts(); }
        if (prop == PRECEDING_JAM) {
            if (value != null && ((Jam) value).getParent() != getParent()) {
                getParent().remove(Period.TIMEOUT, this);
                parent = ((Jam) value).getParent();
                getParent().add(Period.TIMEOUT, this);
            }
            if (getOwner() instanceof Team) { ((Team) getOwner()).recountTimeouts(); }
            if (value == scoreBoard.getCurrentPeriod().getCurrentJam()
                    || last == scoreBoard.getCurrentPeriod().getCurrentJam()) {
                scoreBoard.set(ScoreBoard.NO_MORE_JAM, scoreBoard.get(ScoreBoard.NO_MORE_JAM), Source.RECALCULATE);
            }
        }
    }

    @Override
    public void delete(Source source) {
        if (get(OWNER) instanceof Team) { ((Team) get(OWNER)).remove(Team.TIME_OUT, this); }
        super.delete(source);
    }

    @Override
    public void execute(Command prop, Source source) {
        synchronized (coreLock) {
            if (prop == DELETE) {
                if (!isRunning()) { delete(source); }
            }
        }
    }

    @Override
    public void stop() {
        set(RUNNING, false);
        set(DURATION, scoreBoard.getClock(Clock.ID_TIMEOUT).getTimeElapsed());
        set(WALLTIME_END, ScoreBoardClock.getInstance().getCurrentWalltime());
        set(PERIOD_CLOCK_ELAPSED_END, scoreBoard.getClock(Clock.ID_PERIOD).getTimeElapsed());
        set(PERIOD_CLOCK_END, scoreBoard.getClock(Clock.ID_PERIOD).getTime());
    }

    @Override
    public TimeoutOwner getOwner() { return get(OWNER); }

    @Override
    public boolean isReview() { return get(REVIEW); }

    @Override
    public boolean isRetained() { return get(RETAINED_REVIEW); }

    @Override
    public boolean isRunning() { return get(RUNNING); }
}
