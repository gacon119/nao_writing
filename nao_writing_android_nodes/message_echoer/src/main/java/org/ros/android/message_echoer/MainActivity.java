/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.message_echoer;

import android.os.Bundle;
import android.util.Log;

import org.ros.address.InetAddressFactory;
import org.ros.android.MessageCallable;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.time.NtpTimeProvider;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

/**
 * The main activity for the message_echoer app, which starts the relevant ROS nodes and configures
 * various callback methods.
 * @author deanna.m.hood@gmail.com (Deanna Hood).
 */


public class MainActivity extends RosActivity {
    private InteractionManagerNode interactionManagerNode;
    private static final java.lang.String TAG = "messageEchoer";

    private SystemDrawingViewNode systemDrawingViewNode;
    private UserDrawingView userDrawingsView;
    private Button buttonClear;
    private Button buttonSend;
    private ArrayList< ArrayList<double[]> > userDrawnMessage = new ArrayList<ArrayList<double[]>>();
    private GestureDetector gestureDetector;
    private boolean longClicked = true;
    public MainActivity() {
        // The RosActivity constructor configures the notification title and ticker
        // messages.
        super("Message Echoer", "Message echoer");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); //remove title bar with app's icon and name
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
              WindowManager.LayoutParams.FLAG_FULLSCREEN); //remove bar with notifications and battery level etc
        Log.e(TAG, "Should be fullscreen now");
        setContentView(R.layout.main);
        buttonClear = (Button)findViewById(R.id.buttonClear);
        buttonClear.setOnClickListener(clearListener); // Register the onClick listener with the implementation below
        buttonSend = (Button)findViewById(R.id.buttonSend);
        buttonSend.setOnClickListener(sendListener); // Register the onClick listener with the implementation above

        userDrawingsView = (UserDrawingView)findViewById(R.id.signature);
        userDrawingsView.setStrokeFinishedCallable(new MessageCallable<Integer, ArrayList<double[]>>() {
            @Override
            public Integer call(ArrayList<double[]>  message) {
                onStrokeDrawingFinished(message);
                return 1;
            }
        });

        systemDrawingViewNode = (SystemDrawingViewNode) findViewById(R.id.image);
        systemDrawingViewNode.setTopicName("write_traj");
        systemDrawingViewNode.setMessageType(nav_msgs.Path._TYPE);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                Log.e(TAG, "Double tap at: ["+String.valueOf(x)+", "+String.valueOf(y)+"]");
                //publish touch event in world coordinates instead of tablet coordinates
                interactionManagerNode.publishGestureInfoMessage(DisplayMethods.PX2M(x), DisplayMethods.PX2M(systemDrawingViewNode.getHeight() - y));
                longClicked = true;
            }
        });
    }

    private void onStrokeDrawingFinished(ArrayList<double[]> points){
        //convert from pixels in 'tablet frame' to metres in 'robot frame'
        for(double[] point : points){
            point[0] = DisplayMethods.PX2M(point[0]);                              //x coordinate
            point[1] = DisplayMethods.PX2M(systemDrawingViewNode.getHeight() - point[1]); //y coordinate
        }
        //interactionManagerNode.publishUserDrawnShapeMessage(points);
        Log.e(TAG, "Adding stroke to message");
        userDrawnMessage.add(points);
    }

    private void onShapeDrawingFinish(){
        Log.e(TAG,"Animation finished!");
        systemDrawingViewNode.publishShapeFinishedMessage();
    }

    private View.OnClickListener sendListener = new View.OnClickListener() {
        public void onClick(View v) {
            Log.e(TAG, "onClick() called - send button");
            interactionManagerNode.publishUserDrawnMessageMessage(userDrawnMessage);
            userDrawnMessage.clear();
            interactionManagerNode.publishClearScreenMessage();  //clear display of robot-drawn message
            userDrawingsView.clear(); //clear display of user-drawn shapes (would have liked to have
            // done this with a callback upon receipt of clearScreenMessage, but that thread isn't allowed to 'touch' signatureView)
        }
    };

    private View.OnClickListener clearListener = new View.OnClickListener() {
        public void onClick(View v) {
            Log.e(TAG, "onClick() called - clear button");
            interactionManagerNode.publishClearScreenMessage();  //clear display of robot-drawn message
            userDrawnMessage.clear();
            userDrawingsView.clear(); //clear display of user-drawn shapes (would have liked to have
                // done this with a callback upon receipt of clearScreenMessage, but that thread isn't allowed to 'touch' signatureView)
        }
    };

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        interactionManagerNode = new InteractionManagerNode();
        interactionManagerNode.setTouchInfoTopicName("touch_info");
        interactionManagerNode.setGestureInfoTopicName("long_touch_info");
        interactionManagerNode.setClearScreenTopicName("clear_screen");
        systemDrawingViewNode.setClearScreenTopicName("clear_screen");
        systemDrawingViewNode.setFinishedShapeTopicName("shape_finished");
        interactionManagerNode.setUserDrawnShapeTopicName("user_drawn_shapes");

        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        // At this point, the user has already been prompted to either enter the URI
        // of a master to use or to start a master locally.
        nodeConfiguration.setMasterUri(getMasterUri());
        String hostIp = getMasterUri().getHost();
        Log.d(TAG, "Host's IP address: "+hostIp);

        NtpTimeProvider ntpTimeProvider = new NtpTimeProvider(InetAddressFactory.newFromHostString(hostIp),nodeMainExecutor.getScheduledExecutorService());
        ntpTimeProvider.startPeriodicUpdates(1, TimeUnit.MINUTES);
        nodeConfiguration.setTimeProvider(ntpTimeProvider);


        // The RosTextView is a NodeMain that must be executed in order to
        // start displaying incoming messages.
        Log.e(TAG, "Ready to execute");
        nodeMainExecutor.execute(systemDrawingViewNode, nodeConfiguration.setNodeName("android_gingerbread/display_manager"));
        nodeMainExecutor.execute(interactionManagerNode, nodeConfiguration.setNodeName("android_gingerbread/interaction_manager"));


        DisplayMethods displayMethods = new DisplayMethods();
        displayMethods.setOnAnimationFinishCallable(new MessageCallable<Integer, Integer>() {
            @Override
            public Integer call(Integer message) {
                onShapeDrawingFinish();
                return 1;
            }
        });

        systemDrawingViewNode.setMessageToDrawableCallable(displayMethods.getTurnPathIntoAnimation());
        displayMethods.setDisplayHeight(systemDrawingViewNode.getHeight());
        displayMethods.setDisplayWidth(systemDrawingViewNode.getWidth());

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                longClicked = false;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if(!longClicked){
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    Log.e(TAG, "Touch at: ["+String.valueOf(x)+", "+String.valueOf(y)+"]");
                    //publish touch event in world coordinates instead of tablet coordinates
                    interactionManagerNode.publishTouchInfoMessage(DisplayMethods.PX2M(x), DisplayMethods.PX2M(systemDrawingViewNode.getHeight() - y));
                }
                break;
        }
        return true;
    }
}





