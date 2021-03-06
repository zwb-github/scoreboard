package com.carolinarollergirls.scoreboard.core.impl;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.carolinarollergirls.scoreboard.core.BoxTrip;
import com.carolinarollergirls.scoreboard.core.Clock;
import com.carolinarollergirls.scoreboard.core.Fielding;
import com.carolinarollergirls.scoreboard.core.FloorPosition;
import com.carolinarollergirls.scoreboard.core.Period;
import com.carolinarollergirls.scoreboard.core.Position;
import com.carolinarollergirls.scoreboard.core.PreparedTeam;
import com.carolinarollergirls.scoreboard.core.PreparedTeam.PreparedTeamSkater;
import com.carolinarollergirls.scoreboard.core.Role;
import com.carolinarollergirls.scoreboard.core.Rulesets;
import com.carolinarollergirls.scoreboard.core.ScoreBoard;
import com.carolinarollergirls.scoreboard.core.ScoringTrip;
import com.carolinarollergirls.scoreboard.core.Skater;
import com.carolinarollergirls.scoreboard.core.Team;
import com.carolinarollergirls.scoreboard.core.TeamJam;
import com.carolinarollergirls.scoreboard.core.Timeout;
import com.carolinarollergirls.scoreboard.event.Child;
import com.carolinarollergirls.scoreboard.event.Command;
import com.carolinarollergirls.scoreboard.event.ConditionalScoreBoardListener;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProvider;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProviderImpl;
import com.carolinarollergirls.scoreboard.event.ScoreBoardListener;
import com.carolinarollergirls.scoreboard.event.Value;
import com.carolinarollergirls.scoreboard.event.ValueWithId;
import com.carolinarollergirls.scoreboard.rules.Rule;
import com.carolinarollergirls.scoreboard.utils.ValWithId;

public class TeamImpl extends ScoreBoardEventProviderImpl<Team> implements Team {
    public TeamImpl(ScoreBoard sb, String i) {
        super(sb, i, ScoreBoard.TEAM);
        addProperties(NAME, LOGO, RUNNING_OR_UPCOMING_TEAM_JAM, RUNNING_OR_ENDED_TEAM_JAM, FIELDING_ADVANCE_PENDING,
                CURRENT_TRIP, SCORE, JAM_SCORE, TRIP_SCORE, LAST_SCORE, TIMEOUTS, OFFICIAL_REVIEWS, LAST_REVIEW,
                IN_TIMEOUT, IN_OFFICIAL_REVIEW, NO_PIVOT, RETAINED_OFFICIAL_REVIEW, LOST, LEAD, CALLOFF, INJURY,
                NO_INITIAL, DISPLAY_LEAD, STAR_PASS, STAR_PASS_TRIP, ALTERNATE_NAME, COLOR, SKATER, POSITION, TIME_OUT,
                BOX_TRIP, ADD_TRIP, REMOVE_TRIP, ADVANCE_FIELDINGS, TIMEOUT, OFFICIAL_REVIEW);
        for (FloorPosition fp : FloorPosition.values()) {
            add(POSITION, new PositionImpl(this, fp));
        }
        addWriteProtection(POSITION);
        setCopy(CURRENT_TRIP, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.CURRENT_TRIP, true);
        setCopy(SCORE, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.TOTAL_SCORE, true);
        setCopy(JAM_SCORE, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.JAM_SCORE, true);
        setCopy(LAST_SCORE, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.LAST_SCORE, true);
        setCopy(TRIP_SCORE, this, CURRENT_TRIP, ScoringTrip.SCORE, false);
        setCopy(LOST, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.LOST, false);
        setCopy(LEAD, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.LEAD, false);
        setCopy(CALLOFF, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.CALLOFF, false);
        setCopy(INJURY, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.INJURY, false);
        setCopy(NO_INITIAL, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.NO_INITIAL, false);
        setCopy(DISPLAY_LEAD, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.DISPLAY_LEAD, false);
        setCopy(NO_PIVOT, this, RUNNING_OR_UPCOMING_TEAM_JAM, TeamJam.NO_PIVOT, false);
        setCopy(STAR_PASS, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.STAR_PASS, false);
        setCopy(STAR_PASS_TRIP, this, RUNNING_OR_ENDED_TEAM_JAM, TeamJam.STAR_PASS_TRIP, false);
        setRecalculated(IN_TIMEOUT).addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.OWNER)
                .addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.REVIEW)
                .addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.RUNNING);
        setRecalculated(IN_OFFICIAL_REVIEW).addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.OWNER)
                .addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.REVIEW)
                .addIndirectSource(sb, ScoreBoard.CURRENT_TIMEOUT, Timeout.RUNNING);
        addWriteProtectionOverride(TIMEOUTS, Source.ANY_INTERNAL);
        addWriteProtectionOverride(OFFICIAL_REVIEWS, Source.ANY_INTERNAL);
        addWriteProtectionOverride(LAST_REVIEW, Source.ANY_INTERNAL);
        setCopy(RETAINED_OFFICIAL_REVIEW, this, LAST_REVIEW, Timeout.RETAINED_REVIEW, false);
        sb.addScoreBoardListener(
                new ConditionalScoreBoardListener<>(Rulesets.class, Rulesets.CURRENT_RULESET, rulesetChangeListener));
    }

    @Override
    protected Object computeValue(Value<?> prop, Object value, Object last, Source source, Flag flag) {
        if (prop == IN_TIMEOUT) {
            Timeout t = scoreBoard.getCurrentTimeout();
            return t.isRunning() && this == t.getOwner() && !t.isReview();
        }
        if (prop == IN_OFFICIAL_REVIEW) {
            Timeout t = scoreBoard.getCurrentTimeout();
            return t.isRunning() && this == t.getOwner() && t.isReview();
        }
        if (prop == TRIP_SCORE && source != Source.COPY) {
            tripScoreTimerTask.cancel();
            if ((Integer) value > 0 && getCurrentTrip().getNumber() == 1 && !getScoreBoard().isInOvertime()) {
                // If points arrive during an initial trip and we are not in overtime, assign
                // the points to the first scoring trip instead.
                getCurrentTrip().set(ScoringTrip.ANNOTATION,
                        "Points were added without Add Trip\n" + getCurrentTrip().get(ScoringTrip.ANNOTATION));
                execute(ADD_TRIP);
            }
            if (scoreBoard.isInJam() && ((Integer) value > 0 || ((Integer) last == 0 && flag != Flag.CHANGE))) {
                // we are during a jam and either points have been entered or the trip score has
                // been explicitly set to 0 - set a timer to advance the trip
                tripScoreTimer.purge();
                tripScoreJamTime = getCurrentTrip().get(ScoringTrip.JAM_CLOCK_END);
                if (tripScoreJamTime == 0L) { tripScoreJamTime = scoreBoard.getClock(Clock.ID_JAM).getTimeElapsed(); }
                tripScoreTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        execute(ADD_TRIP);
                        getCurrentTrip().getPrevious().set(ScoringTrip.JAM_CLOCK_END, tripScoreJamTime);
                    }
                };
                tripScoreTimer.schedule(tripScoreTimerTask, 4000);
            }
        }
        if (prop == NO_INITIAL && source != Source.COPY) {
            if (!(Boolean) value && (Boolean) last) {
                execute(ADD_TRIP, source);
            } else if ((Boolean) value && !(Boolean) last && getCurrentTrip().getNumber() == 2 && get(JAM_SCORE) == 0) {
                execute(REMOVE_TRIP, Source.OTHER);
            }
        }
        if (prop == INJURY && source != Source.COPY && (Boolean) value) { set(CALLOFF, false); }
        if (prop == CALLOFF && source != Source.COPY && (Boolean) value) { set(INJURY, false); }
        return value;
    }

    @Override
    protected void valueChanged(Value<?> prop, Object value, Object last, Source source, Flag flag) {
        if (prop == LEAD && (Boolean) value && scoreBoard.isInJam()) {
            if (getCurrentTrip().getNumber() == 1) { getRunningOrEndedTeamJam().addScoringTrip(); }
            if (getOtherTeam().isLead()) { getOtherTeam().set(LEAD, false); }
        } else if (prop == STAR_PASS) {
            if (getPosition(FloorPosition.JAMMER).getSkater() != null) {
                getPosition(FloorPosition.JAMMER).getSkater()
                        .setRole(FloorPosition.JAMMER.getRole(getRunningOrUpcomingTeamJam()));
            }
            if (getPosition(FloorPosition.PIVOT).getSkater() != null) {
                getPosition(FloorPosition.PIVOT).getSkater()
                        .setRole(FloorPosition.PIVOT.getRole(getRunningOrUpcomingTeamJam()));
            }
            if ((Boolean) value && isLead()) { set(LOST, true); }
        } else if ((prop == CALLOFF || prop == INJURY) && scoreBoard.isInJam() && (Boolean) value) {
            scoreBoard.stopJamTO();
        }
    }

    @Override
    public void execute(Command prop, Source source) {
        if (prop == ADD_TRIP) {
            tripScoreTimerTask.cancel();
            getRunningOrEndedTeamJam().addScoringTrip();
            if (!isLead() && !getScoreBoard().getTeam(Team.ID_1.equals(getId()) ? Team.ID_2 : Team.ID_1).isLead()) {
                set(LOST, true);
            }
        } else if (prop == REMOVE_TRIP) {
            if (!tripScoreTimerTask.cancel()) {
                getRunningOrEndedTeamJam().removeScoringTrip();
            }
        } else if (prop == ADVANCE_FIELDINGS) {
            advanceFieldings();
        } else if (prop == OFFICIAL_REVIEW) {
            officialReview();
        } else if (prop == TIMEOUT) {
            timeout();
        }
    }

    @Override
    public ScoreBoardEventProvider create(Child<?> prop, String id, Source source) {
        synchronized (coreLock) {
            if (prop == SKATER) {
                return new SkaterImpl(this, id);
            } else if (prop == BOX_TRIP) {
                return new BoxTripImpl(this, id);
            }
            return null;
        }
    }

    @Override
    protected void itemAdded(Child<?> prop, ValueWithId item, Source source) {
        if (prop == TIME_OUT) { recountTimeouts(); }
    }

    @Override
    protected void itemRemoved(Child<?> prop, ValueWithId item, Source source) {
        if (prop == SKATER) { ((Skater) item).delete(); }
        if (prop == TIME_OUT) { recountTimeouts(); }
    }

    @Override
    public ScoreBoard getScoreBoard() { return scoreBoard; }

    @Override
    public void reset() {
        synchronized (coreLock) {
            setName(DEFAULT_NAME_PREFIX + getId());
            setLogo(DEFAULT_LOGO);
            set(RUNNING_OR_UPCOMING_TEAM_JAM, null);
            set(RUNNING_OR_ENDED_TEAM_JAM, null);
            set(FIELDING_ADVANCE_PENDING, false);

            for (Position p : getAll(POSITION)) {
                p.reset();
            }
            removeAll(BOX_TRIP);
            removeAll(ALTERNATE_NAME);
            removeAll(COLOR);
            removeAll(SKATER);
            removeAll(TIME_OUT);
        }
    }

    @Override
    public String getName() { return get(NAME); }

    @Override
    public void setName(String n) { set(NAME, n); }

    @Override
    public void startJam() {
        synchronized (coreLock) {
            advanceFieldings(); // if this hasn't been manually triggered between jams, do it now
            getCurrentTrip().set(ScoringTrip.CURRENT, true);
        }
    }

    @Override
    public void stopJam() {
        synchronized (coreLock) {
            if (isDisplayLead() && !scoreBoard.getClock(Clock.ID_JAM).isTimeAtEnd() && !isInjury()
                    && !getOtherTeam().isInjury()) {
                set(CALLOFF, true);
            }
            getCurrentTrip().set(ScoringTrip.CURRENT, false);

            set(FIELDING_ADVANCE_PENDING, true);

            updateTeamJams();

            Map<Skater, Role> toField = new HashMap<>();
            TeamJam upcomingTJ = getRunningOrUpcomingTeamJam();
            TeamJam endedTJ = getRunningOrEndedTeamJam();
            for (FloorPosition fp : FloorPosition.values()) {
                Skater s = endedTJ.getFielding(fp).getSkater();
                if (s != null && (endedTJ.getFielding(fp).isInBox() || s.hasUnservedPenalties())) {
                    if (fp.getRole(endedTJ) != fp.getRole(upcomingTJ)) {
                        toField.put(s, fp.getRole(endedTJ));
                    } else {
                        upcomingTJ.getFielding(fp).setSkater(s);
                        BoxTrip bt = endedTJ.getFielding(fp).getCurrentBoxTrip();
                        if (bt != null && bt.isCurrent()) {
                            bt.add(BoxTrip.FIELDING, upcomingTJ.getFielding(fp));
                        }
                    }
                }
            }
            nextReplacedBlocker = FloorPosition.PIVOT;
            for (Skater s : toField.keySet()) {
                field(s, toField.get(s), upcomingTJ);
                BoxTrip bt = s.getFielding(endedTJ).getCurrentBoxTrip();
                if (bt != null && bt.isCurrent()) { bt.add(BoxTrip.FIELDING, s.getFielding(upcomingTJ)); }
            }

            for (Skater s : getAll(SKATER)) {
                s.updateEligibility();
            }
        }
    }

    private void advanceFieldings() {
        set(FIELDING_ADVANCE_PENDING, false);
        updateTeamJams();
    }

    @Override
    public TeamSnapshot snapshot() {
        synchronized (coreLock) {
            return new TeamSnapshotImpl(this);
        }
    }

    @Override
    public void restoreSnapshot(TeamSnapshot s) {
        synchronized (coreLock) {
            if (s.getId() != getId()) { return; }
            set(FIELDING_ADVANCE_PENDING, s.getFieldingAdvancePending());
            updateTeamJams();
            if (scoreBoard.isInJam()) {
                set(CALLOFF, false);
                set(INJURY, false);
            }
        }
    }

    @Override
    public String getAlternateName(String i) { return get(ALTERNATE_NAME, i).getValue(); }

    @Override
    public String getAlternateName(AlternateNameId id) { return getAlternateName(id.toString()); }

    @Override
    public void setAlternateName(String i, String n) {
        synchronized (coreLock) {
            add(ALTERNATE_NAME, new ValWithId(i, n));
        }
    }

    @Override
    public void removeAlternateName(String i) { remove(ALTERNATE_NAME, i); }

    @Override
    public String getColor(String i) { return get(COLOR, i).getValue(); }

    @Override
    public void setColor(String i, String c) {
        synchronized (coreLock) {
            add(COLOR, new ValWithId(i, c));
        }
    }

    @Override
    public void removeColor(String i) { remove(COLOR, i); }

    @Override
    public String getLogo() { return get(LOGO); }

    @Override
    public void setLogo(String l) { set(LOGO, l); }

    @Override
    public void loadPreparedTeam(PreparedTeam pt) {
        synchronized (coreLock) {
            setLogo(pt.get(PreparedTeam.LOGO));
            setName(pt.get(PreparedTeam.NAME));
            for (ValWithId v : pt.getAll(PreparedTeam.ALTERNATE_NAME)) {
                setAlternateName(v.getId(), v.getValue());
            }
            for (ValWithId v : pt.getAll(PreparedTeam.COLOR)) {
                setColor(v.getId(), v.getValue());
            }
            for (PreparedTeamSkater pts : pt.getAll(PreparedTeam.SKATER)) {
                addSkater(new SkaterImpl(this, pts));
            }
        }
    }

    @Override
    public void timeout() {
        synchronized (coreLock) {
            if (getTimeouts() > 0) { getScoreBoard().setTimeoutType(this, false); }
        }
    }

    @Override
    public void officialReview() {
        synchronized (coreLock) {
            if (getOfficialReviews() > 0) { getScoreBoard().setTimeoutType(this, true); }
        }
    }

    @Override
    public TeamJam getRunningOrUpcomingTeamJam() { return get(RUNNING_OR_UPCOMING_TEAM_JAM); }

    @Override
    public TeamJam getRunningOrEndedTeamJam() { return get(RUNNING_OR_ENDED_TEAM_JAM); }

    @Override
    public void updateTeamJams() {
        synchronized (coreLock) {
            set(RUNNING_OR_ENDED_TEAM_JAM, scoreBoard.getCurrentPeriod().getCurrentJam().getTeamJam(getId()));
            set(RUNNING_OR_UPCOMING_TEAM_JAM,
                    scoreBoard.isInJam() ? getRunningOrEndedTeamJam() : getRunningOrEndedTeamJam().getNext());
            for (Position p : getAll(POSITION)) {
                p.updateCurrentFielding();
            }
            for (Skater v : getAll(SKATER)) {
                v.updateFielding(
                        hasFieldingAdvancePending() ? getRunningOrEndedTeamJam() : getRunningOrUpcomingTeamJam());
            }
        }
    }

    @Override
    public int getScore() { return get(SCORE); }

    @Override
    public ScoringTrip getCurrentTrip() { return get(CURRENT_TRIP); }

    public boolean cancelTripAdvancement() { return tripScoreTimerTask.cancel(); }

    @Override
    public boolean inTimeout() { return get(IN_TIMEOUT); }

    @Override
    public boolean inOfficialReview() { return get(IN_OFFICIAL_REVIEW); }

    @Override
    public boolean retainedOfficialReview() { return get(RETAINED_OFFICIAL_REVIEW); }

    @Override
    public void setRetainedOfficialReview(boolean b) { set(RETAINED_OFFICIAL_REVIEW, b); }

    @Override
    public int getTimeouts() { return get(TIMEOUTS); }

    @Override
    public int getOfficialReviews() { return get(OFFICIAL_REVIEWS); }

    @Override
    public void recountTimeouts() {
        boolean toPerPeriod = scoreBoard.getRulesets().getBoolean(Rule.TIMEOUTS_PER_PERIOD);
        boolean revPerPeriod = scoreBoard.getRulesets().getBoolean(Rule.REVIEWS_PER_PERIOD);
        int toCount = scoreBoard.getRulesets().getInt(Rule.NUMBER_TIMEOUTS);
        int revCount = scoreBoard.getRulesets().getInt(Rule.NUMBER_REVIEWS);
        int retainsLeft = scoreBoard.getRulesets().getInt(Rule.NUMBER_RETAINS);
        boolean rdclPerHalfRules = scoreBoard.getRulesets().getBoolean(Rule.RDCL_PER_HALF_RULES);
        boolean otherHalfToUnused = rdclPerHalfRules;
        Timeout lastReview = null;

        for (Timeout t : getAll(TIME_OUT)) {
            boolean isThisRdclHalf = false;
            if (rdclPerHalfRules) {
                boolean gameIsSecondHalf = scoreBoard.getCurrentPeriodNumber() > 2;
                boolean tIsSecondHalf = ((Period) t.getParent()).getNumber() > 2;
                isThisRdclHalf = (gameIsSecondHalf == tIsSecondHalf);
            }
            if (t.isReview()) {
                if (!revPerPeriod || t.getParent() == scoreBoard.getCurrentPeriod() || isThisRdclHalf) {
                    if (retainsLeft > 0 && t.isRetained()) {
                        retainsLeft--;
                    } else if (revCount > 0) {
                        revCount--;
                    }
                    if (lastReview == null || t.compareTo(lastReview) > 0) {
                        lastReview = t;
                    }
                }
            } else {
                if (toCount > 0 && (!toPerPeriod || t.getParent() == scoreBoard.getCurrentPeriod())) {
                    toCount--;
                    otherHalfToUnused = otherHalfToUnused && !isThisRdclHalf;
                }
            }
        }
        if (otherHalfToUnused) { toCount--; }
        set(TIMEOUTS, toCount);
        set(OFFICIAL_REVIEWS, revCount);
        set(LAST_REVIEW, lastReview);
    }

    protected ScoreBoardListener rulesetChangeListener = new ScoreBoardListener() {
        @Override
        public void scoreBoardChange(ScoreBoardEvent<?> event) { recountTimeouts(); }
    };

    @Override
    public Skater getSkater(String id) { return get(SKATER, id); }

    public Skater addSkater(String id) { return getOrCreate(SKATER, id); }

    @Override
    public void addSkater(Skater skater) { add(SKATER, skater); }

    @Override
    public void removeSkater(String id) { remove(SKATER, id); }

    @Override
    public Position getPosition(FloorPosition fp) {
        return fp == null ? null : get(POSITION, fp.toString());
    }

    @Override
    public void field(Skater s, Role r) {
        field(s, r, hasFieldingAdvancePending() ? getRunningOrEndedTeamJam() : getRunningOrUpcomingTeamJam());
    }

    @Override
    public void field(Skater s, Role r, TeamJam tj) {
        synchronized (coreLock) {
            if (s == null) { return; }
            if (s.getFielding(tj) != null && s.getFielding(tj).getPosition() == getPosition(FloorPosition.PIVOT)) {
                tj.setNoPivot(r != Role.PIVOT);
                if ((r == Role.BLOCKER || r == Role.PIVOT) && ((tj.isRunningOrEnded() && hasFieldingAdvancePending())
                        || (tj.isRunningOrUpcoming() && !hasFieldingAdvancePending()))) {
                    s.setRole(r);
                }
            }
            if (s.getFielding(tj) == null || s.getRole(tj) != r) {
                Fielding f = getAvailableFielding(r, tj);
                if (r == Role.PIVOT && f != null) {
                    if (f.getSkater() != null && (tj.hasNoPivot() || s.getRole(tj) == Role.BLOCKER)) {
                        // If we are moving a blocker to pivot, move the previous pivot to blocker
                        // If we are replacing a blocker from the pivot spot,
                        // see if we have a blocker spot available for them instead
                        Fielding f2;
                        if (s.getRole(tj) == Role.BLOCKER) {
                            f2 = s.getFielding(tj);
                        } else {
                            f2 = getAvailableFielding(Role.BLOCKER, tj);
                        }
                        f2.setSkater(f.getSkater());
                    }
                    f.setSkater(s);
                    tj.setNoPivot(false);
                } else if (f != null) {
                    f.setSkater(s);
                } else {
                    s.remove(Skater.FIELDING, s.getFielding(tj));
                }
            }
        }
    }

    private Fielding getAvailableFielding(Role r, TeamJam tj) {
        switch (r) {
        case JAMMER:
            if (tj.isStarPass()) {
                return tj.getFielding(FloorPosition.PIVOT);
            } else {
                return tj.getFielding(FloorPosition.JAMMER);
            }
        case PIVOT:
            if (tj.isStarPass()) {
                return null;
            } else {
                return tj.getFielding(FloorPosition.PIVOT);
            }
        case BLOCKER:
            Fielding[] fs = { tj.getFielding(FloorPosition.BLOCKER1), tj.getFielding(FloorPosition.BLOCKER2),
                    tj.getFielding(FloorPosition.BLOCKER3) };
            for (Fielding f : fs) {
                if (f.getSkater() == null) { return f; }
            }
            Fielding fourth = tj.getFielding(tj.isStarPass() ? FloorPosition.JAMMER : FloorPosition.PIVOT);
            if (fourth.getSkater() == null) { return fourth; }
            int tries = 0;
            do {
                if (++tries > 4) { return null; }
                switch (nextReplacedBlocker) {
                case BLOCKER1:
                    nextReplacedBlocker = FloorPosition.BLOCKER2;
                    break;
                case BLOCKER2:
                    nextReplacedBlocker = FloorPosition.BLOCKER3;
                    break;
                case BLOCKER3:
                    nextReplacedBlocker = (tj.hasNoPivot() && !tj.isStarPass()) ? FloorPosition.PIVOT
                            : FloorPosition.BLOCKER1;
                    break;
                case PIVOT:
                    nextReplacedBlocker = FloorPosition.BLOCKER1;
                    break;
                default:
                    break;
                }
            } while (tj.getFielding(nextReplacedBlocker).isInBox());
            return tj.getFielding(nextReplacedBlocker);
        default:
            return null;
        }
    }

    @Override
    public boolean hasFieldingAdvancePending() { return get(FIELDING_ADVANCE_PENDING); }

    @Override
    public boolean isLost() { return get(LOST); }

    @Override
    public boolean isLead() { return get(LEAD); }

    @Override
    public boolean isCalloff() { return get(CALLOFF); }

    @Override
    public boolean isInjury() { return get(INJURY); }

    @Override
    public boolean isDisplayLead() { return get(DISPLAY_LEAD); }

    protected boolean isFieldingStarPass() {
        if (hasFieldingAdvancePending()) {
            return getRunningOrEndedTeamJam().isStarPass();
        } else {
            return getRunningOrUpcomingTeamJam().isStarPass();
        }
    }

    @Override
    public boolean isStarPass() { return get(STAR_PASS); }

    public void setStarPass(boolean sp) { set(STAR_PASS, sp); }

    @Override
    public boolean hasNoPivot() { return get(NO_PIVOT); }

    @Override
    public Team getOtherTeam() {
        String otherId = getId().equals(Team.ID_1) ? Team.ID_2 : Team.ID_1;
        return getScoreBoard().getTeam(otherId);
    }

    FloorPosition nextReplacedBlocker = FloorPosition.PIVOT;

    private Timer tripScoreTimer = new Timer();
    private TimerTask tripScoreTimerTask = new TimerTask() {
        @Override
        public void run() {} // dummy, so the variable is not
                             // null at the first score entry
    };
    private long tripScoreJamTime; // store the jam clock when starting the timer so we can set the correct value
    // when advancing the trip

    public static final String DEFAULT_NAME_PREFIX = "Team ";
    public static final String DEFAULT_LOGO = "";

    public static class TeamSnapshotImpl implements TeamSnapshot {
        private TeamSnapshotImpl(Team team) {
            id = team.getId();
            fieldingAdvancePending = team.hasFieldingAdvancePending();
        }

        @Override
        public String getId() { return id; }

        @Override
        public boolean getFieldingAdvancePending() { return fieldingAdvancePending; }

        protected String id;
        protected boolean fieldingAdvancePending;
    }
}
