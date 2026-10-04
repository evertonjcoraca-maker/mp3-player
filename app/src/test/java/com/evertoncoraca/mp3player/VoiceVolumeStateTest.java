package com.evertoncoraca.mp3player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VoiceVolumeStateTest {
    @Test public void deltaClampsToZeroAndHundred() {
        VoiceVolumeState state = new VoiceVolumeState(95);
        state.changeBy(10);
        assertEquals(100, state.current());
        state.set(5);
        state.changeBy(-10);
        assertEquals(0, state.current());
    }

    @Test public void muteRemembersExactPreviousLevelAndUnmuteRestoresIt() {
        VoiceVolumeState state = new VoiceVolumeState(60);
        state.mute();
        assertEquals(0, state.current());
        state.mute();
        state.unmute();
        assertEquals(60, state.current());
    }

    @Test public void settingZeroIsNotMuteAndDoesNotCreateRestorePoint() {
        VoiceVolumeState state = new VoiceVolumeState(70);
        state.set(0);
        state.unmute();
        assertEquals(0, state.current());
    }

    @Test public void manualSetAfterMuteCancelsMuteRestoreState() {
        VoiceVolumeState state = new VoiceVolumeState(70);
        state.mute();
        state.set(30);
        state.unmute();
        assertEquals(30, state.current());
    }
}
