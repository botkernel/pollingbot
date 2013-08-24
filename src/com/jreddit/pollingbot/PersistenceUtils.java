package com.jreddit.pollingbot;

import java.io.*;
import java.util.*;

import com.almworks.sqlite4java.*;
import com.jreddit.botkernel.*;

/**
 *
 * Persistence Utilities
 *
 */
public class PersistenceUtils {

    /**
     *
     *  Location of the db file. This will be relative to the
     *  working directory of the botkernel we are running in.
     */
    private static final String DB_FILE = 
                            "../pollingbot/scratch/pollingbot.db";

    //
    // NOTE How to check for sqlite tables defined in the schema
    //
    // $ sqlite3 scratch/bots.db
    // sqlite> SELECT * FROM sqlite_master WHERE type='table';
    //

    //
    // Disable verbose sqlite logging
    //
    static {
        java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(java.util.logging.Level.OFF);
    }

    private static Object DB_LOCK = new Object();

    /**
     *
     * Expose a DB lock to callers.
     *
     * Not sure about this sqlite library and transactions, so rather than
     * deal with that, in a threaded env, callers can lock at the java level.
     *
     */
    public static Object getDatabaseLock() { return DB_LOCK; }

    /**
     *
     * Add a ban
     * 
     *
     * @param subreddit     The subreddit name
     *
     */
    public static void addBan(String subreddit) {

        synchronized(DB_LOCK) {
            if(isBanned(subreddit)) {
                return;
            }

            //
            // This might be a bit counter intuitive, but we will default
            // to true here so that the bot doesn't go spam replying
            // if the db connection somehow fails.
            //
            boolean ret = true;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "INSERT INTO bans (subreddit) VALUES (?)" );

                try {
                    st.bind(1, subreddit);
                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

        }
    }
 

    /**
     *
     * Return true if the bot is banned from this sub.
     * False otherwise.
     *
     * @param subreddit     The subreddit name
     *
     */
    public static boolean isBanned(String subreddit) {
        synchronized(DB_LOCK) {

            //
            // This might be a bit counter intuitive, but we will default
            // to true here so that the bot doesn't go spam replying
            // if the db connection somehow fails.
            //
            boolean ret = true;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT subreddit " +
                    " FROM bans " +
                    " WHERE subreddit = ?");

                try {
                    st.bind(1, subreddit);
                    if(st.step()) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }
 
    /**
     *
     * Return true if the bot has replied to the specified thing.
     * False otherwise.
     *
     * @param id    The id of a Thing
     *
     */
    public static boolean isBotReplied(String id) {
        synchronized(DB_LOCK) {

            //
            // This might be a bit counter intuitive, but we will default
            // to true here so that the bot doesn't go spam replying
            // if the db connection somehow fails.
            //
            boolean ret = true;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT user_comment_id " +
                    " FROM matches " +
                    " WHERE user_comment_id = ?");

                try {
                    st.bind(1, id);
                    if(st.step()) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }
   
    /**
     * 
     * Set a thing as having been replied to by the bot.
     *
     * @param id    The id of a Thing
     *
     */
    public static void setBotReplied(String id) {
        synchronized(DB_LOCK) {

            try {
                //
                // TODO Should this connection be cached rather 
                // than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "INSERT INTO matches (user_comment_id) " +
                    " VALUES (?)" );

                try {
                    st.bind(1, id);
                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }
        }
    }

    /**
     *
     * Check if the user has already voted in the speficied poll.
     *
     * @param username  The username of the player
     * @param id        The id of the poll
     *
     */
    public static boolean isVotePresent(String username, int id) {
        synchronized(DB_LOCK) {

            //
            // Default to true
            //
            boolean ret = true;
            
            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT username " +
                    " FROM poll_votes " +
                    " WHERE username = ? AND poll_id = ?");

                try {
                    st.bind(1, username);
                    st.bind(2, id);
                    if(st.step()) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }
 
    /**
     * 
     * Set a vote
     *
     * @param username  The name of the voting user
     * @param id        The id of the poll
     * @param vote      The option id
     *
     */
    public static void setUserVote(String username, int id, int option) {

        synchronized(DB_LOCK) {

            try {
                //
                // TODO Should this connection be cached rather 
                // than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = null;
               
                try {
                
                    st = db.prepare(
                            "INSERT INTO poll_votes " + 
                            " (poll_id, option_num, username) " +
                            " VALUES (?, ?, ?)" );
                    st.bind(1, id);
                    st.bind(2, option);
                    st.bind(3, username);

                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }
        }
    }

    /**
     *
     * Query for poll results
     *
     * @param id    The id of the poll
     *
     * @return the poll info (option => vote map)
     *
     */
    public static Map<Integer, Integer> getPollVotes(int id) {
    
        Map<Integer, Integer> ret = new HashMap<Integer, Integer>();

        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT option_num " +
                    " FROM poll_votes " +
                    " WHERE poll_id = ?" );
                
                try {
                    st.bind(1, id);
                    while(st.step()) {
                        int option = st.columnInt(0);
                        Integer votes = ret.get(new Integer(option));
                        if(votes == null) {
                            ret.put(new Integer(option), new Integer(1));
                        } else {
                            ret.put(
                                    new Integer(option), 
                                    new Integer(votes.intValue() + 1));
                        }
                    } 
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }

    /**
     *
     * Query for a poll
     *
     * @param id    The id of the poll
     *
     * @return the poll options
     *
     */
    public static Poll getPoll(int id) {
    
        Poll ret = null;

        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT user_comment_id, poll_comment_id, poll_id, title " +
                    " FROM polls " +
                    " WHERE poll_id = ?" );
                
                try {
                    st.bind(1, id);
                    if(st.step()) {
                        String ucId = st.columnString(0);
                        String pcId = st.columnString(1);
                        int pId = st.columnInt(2);
                        String title = st.columnString(3);
                        ret = new Poll(pId, title, ucId, pcId);
                    } 
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }


    /**
     *
     * Query for poll options
     *
     * @param id    The id of the poll
     *
     * @return the poll options
     *
     */
    public static PollOption[] getPollOptions(int id) {
    
        List<PollOption> ret = new ArrayList<PollOption>();

        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT option_num, option_value " +
                    " FROM poll_options " +
                    " WHERE poll_id = ?" );
                
                try {
                    st.bind(1, id);
                    while(st.step()) {
                        int option = st.columnInt(0);
                        String value = st.columnString(1);
                        ret.add(
                            new PollOption(option, value) );
                    } 
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return (PollOption[])ret.toArray(new PollOption[0]);
        }
    }

    /**
     *
     * Set poll options
     *
     * @param id        The id of the poll
     * @param options   The poll options
     *
     */
    public static void setPollOptions(int id, PollOption[] pollOptions) {
    
        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);
                db.exec("BEGIN TRANSACTION;");

                SQLiteStatement st = db.prepare(
                    "INSERT INTO poll_options " +
                    "   (poll_id, option_num, option_value) " + 
                    " VALUES (?, ?, ?)" );
                
                try {
                    for(PollOption pollOption: pollOptions) {
                        st.bind(1, id);
                        st.bind(2, pollOption.getOption());
                        st.bind(3, pollOption.getValue());
                        st.step();
                        st.reset();
                    } 
                } finally {
                    st.dispose();
                }
                db.exec("COMMIT;");
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

        }
    }

    /**
     *
     * Create a poll in the db
     *
     * @param userCommentId         The id of the user comment requesting 
     *                              this poll.
     * @param title                 The poll title (question)
     *
     */
    public static int createPoll(String userCommentId, String title) {
   
        int ret = -1;

        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "INSERT INTO polls " +
                    "   (user_comment_id, poll_id, title) " + 
                    " VALUES (?, " +
                    "   (SELECT IFNULL(MAX(poll_id), 0) + 1 FROM polls), " +
                    " ?)" );
                
                try {
                    st.bind(1, userCommentId);
                    st.bind(2, title);
                    st.step();
                } finally {
                    st.dispose();
                }

                st = db.prepare(
                    "SELECT poll_id FROM polls " +
                    " WHERE user_comment_id = ?");
                
                try {
                    st.bind(1, userCommentId);
                    if(st.step()) {
                        ret = st.columnInt(0);
                    }
                } finally {
                    st.dispose();
                }

                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }

    /**
     *
     * Once we have posted the poll and know the comment id, update it in the
     * poll database.
     *
     */
    public static void setPollCommentId(int id, String pollCommentId) {
    
        synchronized(DB_LOCK) {

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "UPDATE polls " +
                    " SET poll_comment_id = ? " +
                    " WHERE poll_id = ?");
                
                try {
                    st.bind(1, pollCommentId);
                    st.bind(2, id);
                    st.step();
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

        }
    }


    /**
     *
     * Check if this user comment already has a poll created for it.
     *
     * @param id        The id of the user comment
     *
     */
    public static boolean isPoll(String userCommentId) {
    
        synchronized(DB_LOCK) {

            boolean ret = true;

            try {

                //
                // TODO Should this connection be 
                // cached rather than instantiated each time?
                //
                SQLiteConnection db = new SQLiteConnection(new File(DB_FILE));
                db.open(true);

                SQLiteStatement st = db.prepare(
                    "SELECT poll_id FROM polls " +
                    " WHERE user_comment_id = ?");
                
                try {
                    st.bind(1, userCommentId);
                    if(st.step()) {
                        ret = true;
                    } else {
                        ret = false;
                    }
                } finally {
                    st.dispose();
                }
                db.dispose();

            } catch(SQLiteException se) {
                se.printStackTrace();
                BotKernel.getBotKernel().log("SEVERE error with database.");
            }

            return ret;
        }
    }


}
