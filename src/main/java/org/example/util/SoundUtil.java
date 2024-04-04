package org.example.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

public class SoundUtil {
    public static void makeSound(int sampleRate, int milliSeconds) throws LineUnavailableException {
        byte[] buf = new byte[ 1 ];
        AudioFormat af = new AudioFormat( (float )sampleRate, 8, 1, true, true );
        SourceDataLine sdl = AudioSystem.getSourceDataLine( af );
        sdl.open();
        sdl.start();
        for( int i = 0; i < milliSeconds * (float )44100 / 1000; i++ ) {
            double angle = i / ( (float )44100 / 440 ) * 2.0 * Math.PI;
            buf[ 0 ] = (byte )( Math.sin( angle ) * 100 );
            sdl.write( buf, 0, 1 );
        }
        sdl.drain();
        sdl.stop();
    }

    public static void main(String[] args) throws LineUnavailableException {
        makeSound(56100, 500);
        makeSound(28100, 100);
    }
}
