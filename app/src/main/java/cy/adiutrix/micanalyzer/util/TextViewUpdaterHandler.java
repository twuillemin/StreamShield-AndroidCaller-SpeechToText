package cy.adiutrix.micanalyzer.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.TextView;

public class TextViewUpdaterHandler {

    public final static int SPEECH_OUTPUT_ACTION_CLEAR = 0;
    public final static int SPEECH_OUTPUT_ACTION_APPEND = 1;
    public final static int SPEECH_OUTPUT_ACTION_APPEND_LINE = 2;

    public static Handler createTextViewHandler(final TextView textView) {
        return new Handler(Looper.myLooper()) {
            public void handleMessage(Message msg) {
                final int what = msg.what;
                switch (what) {
                    case SPEECH_OUTPUT_ACTION_CLEAR: {
                        textView.setText("");
                        break;
                    }
                    case SPEECH_OUTPUT_ACTION_APPEND: {
                        final String txt = msg.obj.toString();
                        textView.append(txt);
                        break;
                    }
                    case SPEECH_OUTPUT_ACTION_APPEND_LINE: {
                        final String txt = msg.obj.toString();
                        textView.append(txt + "\n");
                        break;
                    }
                }
            }
        };
    }
}
