/*
 * Copyright 2014 OpenMarket Ltd
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
 */

package im.vector.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.AdapterUtils;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;
import im.vector.contacts.Contact;

public class VectorAddParticipantsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorAddActivity";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorAddParticipantsActivity.EXTRA_ROOM_ID";

    // creation mode : the members are listed to create a new room
    // edition mode (by default) : the members are dynamically added/removed
    public static final String EXTRA_EDITION_MODE = "VectorAddParticipantsActivity.EXTRA_EDITION_MODE";

    // in creation mode, this is the key to retrieve the users IDs liste
    public static final String RESULT_USERS_ID = "VectorAddParticipantsActivity.RESULT_USERS_ID";

    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private MXMediasCache mxMediasCache;

    private EditText mSearchEdit;
    private TextView mListViewHeaderView;
    private Button mCancelButton;
    private ListView mParticantsListView;

    private VectorAddParticipantsAdapter mAdapter;
    private boolean mIsEditionMode = true;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            VectorAddParticipantsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        mAdapter.listOtherMembers();
                        mAdapter.refresh();
                        refreshListViewHeader();
                    }
                }
            });
        }
    };

    /**
     * Refresh the ListView Header.
     * It is displayed only when there is no search in progress.
     */
    private void refreshListViewHeader() {
        if (TextUtils.isEmpty(mSearchEdit.getText())) {
            mListViewHeaderView.setVisibility(View.VISIBLE);

            if (1 < mAdapter.getCount()) {
                mListViewHeaderView.setText(getString(R.string.room_participants_multi_participants, mAdapter.getCount()));
            } else {
                mListViewHeaderView.setText(getString(R.string.room_participants_one_participant));
            }
        } else {
            mListViewHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        // creation mode : the members are listed to create a new room
        // edition mode (by default) : the members are dynamically added/removed
        mIsEditionMode = intent.hasExtra(EXTRA_EDITION_MODE);

        if (mIsEditionMode && !intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        String matrixId = null;
        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        mSession = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

        if (null == mSession) {
            finish();
            return;
        }

        if (mIsEditionMode) {
            mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
            mRoom = mSession.getDataHandler().getRoom(mRoomId);
        }

        mxMediasCache = mSession.getMediasCache();

        setContentView(R.layout.activity_vector_add_participants);

        mListViewHeaderView = (TextView)findViewById(R.id.add_participants_listview_header_textview);

        mParticantsListView = (ListView)findViewById(R.id.add_participants_members_list);
        mAdapter = new VectorAddParticipantsAdapter(this, R.layout.adapter_item_vector_add_participants, mSession, mIsEditionMode, mRoomId, mxMediasCache);
        mParticantsListView.setAdapter(mAdapter);

        mParticantsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mIsEditionMode) {
                    if (!TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
                        ParticipantAdapterItem participant = mAdapter.getItem(position);
                        if (null == participant.mUserId) {
                            // check if it is a contact
                            if (null != participant.mContact) {
                                final Contact contact = participant.mContact;

                                if (null != contact) {
                                    final ArrayList<String> choicesList = new ArrayList<String>();
                                    final Activity activity = VectorAddParticipantsActivity.this;

                                    if (AdapterUtils.canSendSms(VectorAddParticipantsActivity.this)) {
                                        choicesList.addAll(contact.mPhoneNumbers);
                                    }

                                    choicesList.addAll(contact.mEmails);

                                    // something to offer
                                    if (choicesList.size() > 0) {
                                        final String[] labels = new String[choicesList.size()];

                                        for (int index = 0; index < choicesList.size(); index++) {
                                            labels[index] = choicesList.get(index);
                                        }

                                        new AlertDialog.Builder(activity)
                                                .setItems(labels, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        String value = labels[which];

                                                        // SMS ?
                                                        if (contact.mPhoneNumbers.indexOf(value) >= 0) {
                                                            AdapterUtils.launchSmsIntent(activity, value, activity.getString(R.string.invitation_message));
                                                        } else {
                                                            // emails
                                                            AdapterUtils.launchEmailIntent(activity, value, activity.getString(R.string.invitation_message));
                                                        }
                                                    }
                                                }).setTitle(activity.getString(R.string.invite_this_user_to_use_matrix)).show();
                                    }
                                }
                            }
                        } else {

                            final ArrayList<String> userIDs = new ArrayList<String>();
                            userIDs.add(participant.mUserId);
                            mRoom.invite(userIDs, new SimpleApiCallback<Void>(VectorAddParticipantsActivity.this) {
                                @Override
                                public void onSuccess(Void info) {
                                    // display something ?
                                }
                            });
                        }

                        // leave the search
                        mSearchEdit.setText("");
                    }
                } else {
                    // --> creation mode
                    // no search -> remove the entry
                    if (TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
                        mAdapter.removeMemberAt(position);
                    } else {
                        // add the entry
                        mAdapter.addParticipantAdapterItem(mAdapter.getItem(position));
                        // leave the search
                        mSearchEdit.setText("");
                    }
                }
            }
        });

        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                refreshListViewHeader();
            }
        });

        mAdapter.setOnParticipantsListener(new VectorAddParticipantsAdapter.OnParticipantsListener() {
            @Override
            public void onRemoveClick(final ParticipantAdapterItem participantItem) {
                String text = VectorAddParticipantsActivity.this.getString(R.string.room_participants_remove_prompt_msg, participantItem.mDisplayName);

                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                        .setTitle(R.string.room_participants_remove_prompt_title)
                        .setMessage(text)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mRoom.kick(participantItem.mUserId, new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        // display something
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        // display something
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        // display something
                                    }
                                });
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }

            @Override
            public void onLeaveClick() {
                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                        .setTitle(R.string.room_participants_leave_prompt_title)
                        .setMessage(VectorAddParticipantsActivity.this.getString(R.string.room_participants_leave_prompt_msg))
                        .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mRoom.leave(new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        // display something
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        // display something
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                    }
                                });
                                VectorAddParticipantsActivity.this.finish();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        });

        mSearchEdit = (EditText)findViewById(R.id.add_participants_search_participants);
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                mAdapter.setSearchedPattern(s.toString());
                refreshListViewHeader();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mCancelButton = (Button)findViewById(R.id.add_participants_cancel_search_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchEdit.setText("");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mIsEditionMode) {
            getMenuInflater().inflate(R.menu.vector_add_participants, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_create) {

            VectorAddParticipantsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // provide the Uri
                    Intent intent = new Intent();
                    ArrayList<String> users = new ArrayList<String>();
                    int count = mAdapter.getCount();

                    for (int index = 0; index < count; index++) {
                        ParticipantAdapterItem item = mAdapter.getItem(index);

                        // add only the registered users (except oneself)
                        if ((null != item.mRoomMember) && !TextUtils.isEmpty(item.mRoomMember.getUserId()) && !item.mRoomMember.getUserId().equals(mSession.getCredentials().userId)) {
                            users.add(item.mRoomMember.getUserId());
                        }
                    }
                    intent.putStringArrayListExtra(RESULT_USERS_ID, users);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (null != mRoom) {
            mRoom.addEventListener(mEventListener);
        }
        mAdapter.listOtherMembers();
        mAdapter.refresh();
        refreshListViewHeader();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }
    }
}
