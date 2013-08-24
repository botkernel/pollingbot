package com.jreddit.pollingbot;

import java.io.*;
import java.util.*;

import com.almworks.sqlite4java.*;
import com.jreddit.botkernel.*;

/**
 *
 * A poll 
 *
 */
public class Poll {

    private int _pId;
    private String _title;
    private String _ucId;
    private String _pcId;

    public Poll(    int pollId, String title, 
                    String userCommentId, String pollCommentId ) {

        _pId = pollId;
        _title = title;
        _ucId = userCommentId;
        _pcId = pollCommentId;
    }

    public int getPollId()      { return _pId; }
    public String getTitle()    { return _title; }
    public String getUserCommentId() { return _ucId; }
    public String getPollCommentId() { return _pcId; }

    public String toString() {
        return "Poll " + _pId   + "\n" +
                "    " + _title + "\n" +
                "    request: " + _ucId + "\n" +
                "    poll   : " + _pcId;
    }

}
