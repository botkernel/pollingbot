package com.jreddit.pollingbot;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.jreddit.botkernel.*;

/**
 *
 * 
 *
 */
public abstract class BaseMatchCriteria implements CrawlerMatchCriteria {

    protected PollingBot _bot;

    public BaseMatchCriteria(PollingBot bot) {
        _bot = bot;
    }

    public boolean match(Thing thing) {

        String body = null;

        //
        // Do not consider my own posts as criteria
        //
        String author = thing.getAuthor();
        if( author != null && author.equals(_bot.getUser().getUsername())) {
            // log("Ignoring my own comment " + thing.getName());
            return false;
        }

        if(thing instanceof Comment) {
            Comment comment = (Comment)thing;
            if(comment.getBody() != null) {
                body = ((Comment)comment).getBody().toLowerCase();
            }
        }

        if(thing instanceof Submission) {
            Submission submission = (Submission)thing;
            if( submission.isSelfPost() && submission.getSelftext() != null) {
                body = submission.getSelftext().toLowerCase();
            }
        }

        if(body == null) {
            // Still nothing at this point, it's not a match.
            return false;
        }

        return matchBody(thing, body);
    }

    protected abstract boolean matchBody(Thing thing, String body);

    public CrawlerListener getCrawlerListener() {
        return _bot;
    }

}

