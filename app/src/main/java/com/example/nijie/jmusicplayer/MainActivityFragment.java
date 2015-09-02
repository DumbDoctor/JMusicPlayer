package com.example.nijie.jmusicplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements View.OnClickListener {

    final static public String LOG_TAG  = "JMusicPlayer";

    SeekBar seekbar;
    EditText et;
    TextView tv;

    PlayerEvents events = new PlayerEvents() {
        @Override public void onStop() {
            seekbar.setProgress(0);
            tv.setText("Playback Stopped.");
        }
        @Override public void onStart(String mime, int sampleRate, int channels, long duration) {
            Log.d(LOG_TAG, "onStart called: " + mime + " sampleRate:" + sampleRate + " channels:" + channels);
            if (duration == 0)
                Toast.makeText(getActivity(), "Live Streaming!", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(getActivity(), "Local Playback!", Toast.LENGTH_SHORT).show();
            tv.setText("Playing content:" + mime + " " + sampleRate + "Hz " + (duration/1000000) + "sec");
        }
        @Override public void onPlayUpdate(int percent, long currentms, long totalms) {
            seekbar.setProgress(percent);
        }
        @Override public void onPlay() {
        }
        @Override public void onError() {
            seekbar.setProgress(0);
            Toast.makeText(getActivity(), "Error!",  Toast.LENGTH_SHORT).show();
            tv.setText("An error has been encountered");
        }
    };

    JPlayer p = new JPlayer(events);

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //NJ inflate the root view
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        //NJ set the listener for all buttons and seek bar

        // set listeners on buttons
        ((Button)rootView.findViewById(R.id.IDBUT_LISTCODEC)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_TESTMP3)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_TESTAAC)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_TESTWMA)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_LOADCUSTOM)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_PLAY)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_PAUSE)).setOnClickListener((View.OnClickListener) this);
        ((Button)rootView.findViewById(R.id.IDBUT_STOP)).setOnClickListener((View.OnClickListener) this);

        seekbar = (SeekBar)rootView.findViewById(R.id.IDSEEKBAR);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                if (fromUser) p.seek(progress);
            }
        });

        et = (EditText)rootView.findViewById(R.id.IDEDITTEXT_TESTSOURCE);
        tv = (TextView)rootView.findViewById(R.id.IDTEXTVIEW_SEEKBAR);

        return rootView;

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        switch (id) {
            case R.id.IDBUT_LISTCODEC:
                showDialog(getActivity(), "Decoders", JPlayer.listCodecs());
              break;

            case R.id.IDBUT_TESTMP3:
                Log.d(LOG_TAG, "Load an audio file from resources.");
                p.setDataSource(getActivity(), R.raw.testmp3);
                Toast.makeText(getActivity(), "Now press play!", Toast.LENGTH_SHORT).show();
                break;

            case R.id.IDBUT_TESTAAC:
                Log.d(LOG_TAG, "Load an audio file from resources.");
                p.setDataSource(getActivity(), R.raw.testaac);
                Toast.makeText(getActivity(), "Now press play!", Toast.LENGTH_SHORT).show();
                break;

            case R.id.IDBUT_TESTWMA:
                Log.d(LOG_TAG, "Load an audio file from resources.");
                p.setDataSource(getActivity(), R.raw.testwma);
                Toast.makeText(getActivity(), "Now press play!", Toast.LENGTH_SHORT).show();
                break;

            case R.id.IDBUT_LOADCUSTOM:
                Log.d(LOG_TAG, "Load an audio file from given location.");
                p.setDataSource(et.getText().toString());
                Toast.makeText(getActivity(), "Now press play!", Toast.LENGTH_SHORT).show();
                break;

            case R.id.IDBUT_PLAY:
                Log.d(LOG_TAG, "Start playing!");
                p.asynchronousPlay();
                break;

            case R.id.IDBUT_PAUSE:
                Log.d(LOG_TAG, "Pause.");
                p.pause();
                Toast.makeText(getActivity(), "Paused! Press Stop to change source", Toast.LENGTH_SHORT).show();
                break;

            case R.id.IDBUT_STOP:
                Log.d(LOG_TAG, "Stop.");
                Toast.makeText(getActivity(), "Now stopped!", Toast.LENGTH_SHORT).show();
                p.stop();
                break;

        }
    }

    private static void showDialog(Context context, String title, String msg) {
        //NJ AlertDialog.Builder is a static class, so it has to be in static context
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(msg)
                .setIcon(R.drawable.ic_launcher)
                .show();
    }
}
