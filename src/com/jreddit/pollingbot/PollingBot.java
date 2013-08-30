package com.jreddit.pollingbot;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.DecimalFormat;

import com.omrlnr.jreddit.*;
import com.omrlnr.jreddit.utils.Utils;

import com.jreddit.botkernel.*;

/**
 *
 * PollingBot
 *
 */
public class PollingBot extends BaseBot implements Bot, CrawlerListener {

    //
    // Unique bot name
    //
    private static final String BOT_NAME = "POLLING_BOT";

    private static final String HOME_CRAWLER_NAME = "POLLING_CRAWLER";

    //
    // Retry limit for RateLimitException
    //
    public static final int RETRY_LIMIT = 5;

    //
    // Home crawler listing limit
    //
    private static final int LIMIT = 25;

    //
    // Time to sleep between checking for messages
    //
    private static final int SLEEP = 60;

    //
    // Config file(s)
    // NOTE these paths are relative to the botkernel working directory,
    // as we do not run the bot from here.
    //
    private static final String CONFIG_FILE = 
                        "../pollingbot/scratch/config.properties";

    protected Date _replyAfterDate;

    private User _user;

    private String _owner;
    private String _subreddit;
    private String _suggestionSub;

    private List<String> _unlimitedSubreddits = new ArrayList<String>();

    private static final String BAR     = "█";
    private static final String SPACE   = "░";

    /**
     *
     * Provide a default no argument constructor for the botkernel to 
     * load this class.
     *
     */
    public PollingBot() { }

    public String getSubreddit() { return _subreddit; }

    public User getUser() { return _user; }

    private CrawlerMatchCriteria _suggestCriteria;
    private CrawlerMatchCriteria _reqCriteria;

    /**
     *
     * Initialize our bot.
     *
     */
    public void init() {

        //
        // Use this to keep track of when we are shutting down
        //
        _shutdown = false;
        
        Properties props = new Properties();
        try {
            log("Loading PollingBot config properties...");
            FileInputStream in = new FileInputStream(CONFIG_FILE);
            props.load(in);
            in.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
            log("ERROR init()'ing " + BOT_NAME);
        }

        //
        // Get user info from properties file
        //
        String username = props.getProperty("username");
        String password = props.getProperty("password");

        _user   = new User(username, password);

        _owner = props.getProperty("owner");

        _suggestionSub = props.getProperty("suggestionSub");

        String unlimitedSubreddits = props.getProperty("unlimitedReddits");
        String[] subs = unlimitedSubreddits.split(",");
        for(String sub: subs) {
            _unlimitedSubreddits.add(sub);
        }

        //
        // In the event we have rebuilt the database,
        // the tables containing poll requests which we have already started
        // will not be present. To avoid spam-starting a bunch of polls
        // we will look at our last comment timestamp and not start polls
        // or log potential requests from before that date.
        //
        // Of course if the database is hosed then the votes are all 
        // screwed. (We could parse the existing poll comments...?)
        //
        _replyAfterDate = new Date();
        try {
            List<Comment> comments = Comments.getUserComments(
                                            _user,
                                            _user.getUsername(),
                                            1 );
            if(comments.size() > 0) {
                Comment comment = comments.get(0);
                Date d = comment.getCreatedDate();
                if(d != null) {
                    _replyAfterDate = d;
                }
            }
        } catch( IOException ioe) {
            log("Could not find last comment posted for " +
                _user.getUsername());
        }




        _subreddit = props.getProperty("subreddit");

        // Connect
        try {
            _user.connect();
        } catch(IOException ioe) {
            log("ERROR conecting user for " + BOT_NAME);
        }

        List<String> subReddits = new ArrayList<String>();
        subReddits.add(_subreddit);

        Crawler homeCrawler = 
                    new Crawler(
                                _user,
                                HOME_CRAWLER_NAME,
                                subReddits,
                                new Submissions.ListingType[] {
                                        Submissions.ListingType.HOT,
                                        Submissions.ListingType.NEW },
                                LIMIT,
                                SLEEP);

        //
        // Register ourselves with the Crawler
        //
        homeCrawler.addListener(this);

        //
        // Create a match criteria for the crawler to notify us
        // when we need to respond to a post.
        //
        _suggestCriteria = new PotentialPollMatchCriteria(this);
        _reqCriteria = new PollRequestMatchCriteria(this);

        //
        // Add out match criteria to the crawler.
        //
        homeCrawler.addMatchCriteria(_reqCriteria);

        //
        // Register the crawler with the kernel.
        //
        BotKernel.getBotKernel().addCrawler(homeCrawler);

        //
        // Add the default crawler for finding potential polls
        //
        Crawler crawler = 
                    CrawlerFactory.getCrawler(CrawlerFactory.DEFAULT_CRAWLER);
        crawler.addMatchCriteria(_suggestCriteria);
        crawler.addListener(this);
        BotKernel.getBotKernel().addCrawler(crawler);
        crawler.wake();

        //
        // Add test crawler
        //
        // crawler = CrawlerFactory.getCrawler(CrawlerFactory.TEST_CRAWLER);
        // crawler.addMatchCriteria(_suggestCriteria);
        // crawler.addListener(this);
        // BotKernel.getBotKernel().addCrawler(crawler);
        // crawler.wake();
 
    }

    /**
     *
     * Return our unique name.
     *
     */
    public String getName() {
        return BOT_NAME;
    }

    /**
     *
     * Called when shutting down.
     *
     */
    public void shutdown() {

        //
        // Indicate to sleeping threads that we need to shut down.
        //
        _shutdown = true;
    }



    /** 
     *
     * Main game loop. Check for players responding to games,
     * and then respond back to player. 
     *
     */
    public void run() {

        /**
         *  Main loop
         */
        while(true) {

            if(_shutdown) {
                return;
            }
        
            //
            // Connect
            //
            try {
                _user.connect();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                log("Error cannot connect.");
                sleep(SLEEP);
                continue;
            }

            //
            // Cache any polls affected, then update each affected poll once.
            //
            Set<String> modifiedPolls = new HashSet<String>();

            //
            // Check messages
            //
            List<Message> messages = new ArrayList<Message>();

            log("INFO Fetching messages.");
            try {
                messages = Messages.getMessages(
                                                _user,
                                                Messages.MessageType.UNREAD );
            } catch (IOException ioe) {
                log("ERROR retrieving messages " + ioe);
            }

            log("INFO Found messages: " + messages.size());

            Set<Integer> affectedPolls = new HashSet<Integer>();

            for(Message message: messages) {

                try {

                    if(!message.getKind().equals(Thing.KIND_MESSAGE)) {
                        // Ignore non PMs, these are post responses probably.
                        log("INFO Ignoring non-PM message " + message);
                        Messages.markAsRead(_user, message);
                        continue;
                    }
                
                    String author = message.getAuthor();
                    if(author == null) {
                        log("INFO Ignoring message with no author (deleted?)");
                        Messages.markAsRead(_user, message);
                        continue;
                    }

                    if(author.equals(_owner) &&
                        message.getSubject().trim().toLowerCase().equals("crawl") ) {
                        log("INFO Handling crawl command");
                        String name = message.getBody().trim();
                        Crawler crawler = CrawlerFactory.getCrawler(name);
                        if(crawler != null) {
                            crawler.addMatchCriteria(_suggestCriteria);
                            crawler.addListener(this);
                            BotKernel.getBotKernel().addCrawler(crawler);
                            crawler.wake();
                        } else {
                            log("ERROR cannot find crawler " + name);
                        }
                        Messages.markAsRead(_user, message);
                        continue;
                    }

                    log("INFO Attempting to parse poll id");

                    //
                    // Parse poll id and vote...
                    //
                    String subject = message.getSubject();
                    String[] subjectItems = subject.split(" ");
                    if(subjectItems.length == 2) {
                        try {
                            int id = Integer.parseInt(subjectItems[1]);

                            log("INFO Checking for votes in poll " + id);

                            Object lock = 
                                        PersistenceUtils.getDatabaseLock();

                            synchronized(lock) {

                                if(PersistenceUtils.isVotePresent(author, id)) {
                                    // This user has already voted. Ignore this.
                                    log("INFO vote already present for user " + author);
                                    Messages.markAsRead(_user, message);
                                    continue;
                                }

                                log("INFO Parsing vote in poll " + id);

                                String body = message.getBody();
                                String[] bodyItems = body.split(" ");
                                if( bodyItems.length == 2 && 
                                    bodyItems[1].length() == 1) {

                                    log("INFO Found vote: " + bodyItems[1]);

                                    char c = bodyItems[1].charAt(0);
                                    if(c >= 'a' && c <= 'z') {
                                        int option = c - 'a';

                                        log("INFO Updating votes with user " +
                                                author + " option " + option);

                                        PersistenceUtils.setUserVote(
                                                                author,
                                                                id,
                                                                option);
                                        affectedPolls.add(new Integer(id));
                                    }
                                }
                            }
    
                        } catch (NumberFormatException nfe) {
                            log("Error parsing poll id");
                            Messages.markAsRead(_user, message);
                            continue;
                        }
                    } else {
                        log("Cannot parse subject " + subject);
                    }

                
                    Messages.markAsRead(_user, message);
                    continue;

                } catch (IOException ioe) {
                    log("ERROR caught " + ioe);
                }

            }

            log("INFO Editing affected polls: " + affectedPolls.size());

            //
            // Extract the vote data for all affected polls and
            // perform edits.
            //
            for(Integer pollId: affectedPolls) {

                log("Fetching poll " + pollId);
                Poll poll = PersistenceUtils.getPoll(pollId);

                log("Poll is " + poll);

                PollOption[] options = 
                                PersistenceUtils.getPollOptions(pollId);
                Map<Integer, Integer> voteMap  = 
                                PersistenceUtils.getPollVotes(pollId);
                
                String text = generatePollText( poll.getPollId(),
                                                poll.getTitle(),
                                                options,
                                                voteMap );
                Comment comment = null;
                try {
                    comment = Comments.getComment(
                                            _user, 
                                            poll.getPollCommentId() );

                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    log("Error caught " + ioe);
                    continue;
                }

                if(comment == null) {
                    continue;
                }

                if(PersistenceUtils.isBanned(comment.getSubreddit())) {
                    log("WARN Cannot update poll results in banned sub " +
                        comment.getSubreddit());
                }


                for(int i = 0; i < RETRY_LIMIT; i++) {
                    
                    try {

                        editComment(poll.getPollCommentId(), text);
                        
                        // break out of the retry loop
                        break;  

                    } catch(BannedUserException bue) {

                        log("WARN Adding ban " + comment.getSubreddit());
                        PersistenceUtils.addBan(comment.getSubreddit());

                    } catch(IOException ioe) {
                        ioe.printStackTrace();
                        log("ERROR Caught IOException editing poll comment.");
                    }
                }
            }

            sleep(SLEEP);
        }
    }

    /**
     *
     * To implement CrawlerListener
     *
     */
    public void handleCrawlerEvent(CrawlerEvent event) {

        if(event.getType() == CrawlerEvent.CRAWLER_COMPLETE
            && !event.getCrawler().getName().equals(HOME_CRAWLER_NAME) ) {

            // We have completed a crawl outside of our home sub.
            // Remove ourselves from this crawler.
            event.getCrawler().removeListener(this);
        }

    }

    /**
     *
     * Generate the markup text representation of a poll.
     *
     */
    public String generatePollText( int pollId,
                                    String title,
                                    PollOption[] options, 
                                    Map<Integer, Integer> voteMap ) {

        StringBuffer sb = new StringBuffer();
        sb.append("Poll question: **" + title + "**  \n\n");

        int total = 0;
        for(int i = 0; i < options.length; i++) {
            Integer o = voteMap.get(new Integer(i));
            if(o != null) {
                total += o.intValue();
            }
        }
        sb.append("Total votes: " + total + "  \n\n");

        for(int i = 0; i < options.length; i++) {
            Integer o = voteMap.get(new Integer(i));
            int votes = 0;
            if(o == null) {
                votes = 0;
            } else {
                votes = o.intValue();
            }

            double percent = ((double)votes / (double)total) * 100.0;

            // 
            // Round to two places
            //
            // log("Percent " + percent);
            if(!Double.isNaN(percent)) {
                DecimalFormat twoDForm = new DecimalFormat("#.##");
                percent = Double.valueOf(twoDForm.format(percent));
            } else {
                percent = 0.0;
            }

            // Round to nearest five.
            int iPercent = (int)percent;
            int tmp = iPercent % 5;
            if(tmp < 3) {
                iPercent -= tmp;
            } else {
                iPercent += 5 - tmp;
            }

            // Convert to increments of 5%
            iPercent = iPercent / 5;
           
            sb.append("    " + ((char)('A'+i)) + ". ");
            for(int j = 0; j < 20; j++) {
                if(j < iPercent) {
                    sb.append(BAR);
                } else {
                    sb.append(SPACE);
                }
            }
            sb.append("  " +
                String.format("%6.2f", percent) + "%  " +
                String.format("%4d", votes) + " votes  \n" );

        }

        //
        // Add poll options with vote links
        //
        sb.append("\n\n");
        for(int i = 0; i < options.length; i++) {
            sb.append("- " + ((char)('A'+i)) + ". ");
            sb.append(options[i].getValue() + " " +
                "\\([vote](http://www.reddit.com/message/compose/?to=" +
                _user.getUsername() + 
                "&message=vote+" + 
                (char)('a'+i) +
                "&subject=Poll+" +
                pollId + ")\\)  \n");
        }

        return sb.toString();
    }

    public Date getReplyAfterDate() { return _replyAfterDate; }

    /**
     *
     * The bot's signature
     *
     */
    private static final String BOT_SIG = 
                "[Create a Poll](/r/PollingBot) | " +
                "[FAQ](http://www.reddit.com/r/PollingBot/wiki/faq) | " +
                "[Contact My Human](http://www.reddit.com/message/compose/?to=BlackjackPitboss)    ";

    /**
     *
     * Edit a comment, append the bot's signature.
     *
     */
    public void editComment(String id, String text) throws IOException {
        text += "\n\n" +
                "----\n" +
                BOT_SIG;

        for(int i = 0; i < RETRY_LIMIT; i++) {
            try {
                Comments.editComment(_user, id, text);
                break;
            } catch (RateLimitException rle) {
                // rle.printStackTrace();
                log("Caught RateLimitException: " + rle.getMessage());
                int sleepSecs = rle.getRetryTime();
                log("Sleeping " + sleepSecs +
                            " seconds to recover from rate limit exception...");
                sleep(sleepSecs);
            }
        }
    }



    /**
     *
     * Send a comment, append the bot's signature.
     *
     */
    public String sendComment(Thing thing, String text) throws IOException {
        text += "\n\n" +
                "----\n" +
                BOT_SIG;
        
        for(int i = 0; i < RETRY_LIMIT; i++) {
            try {
                String ret = Comments.comment(_user, thing, text);
                return ret;
            } catch (RateLimitException rle) {
                // rle.printStackTrace();
                log("Caught RateLimitException: " + rle.getMessage());
                int sleepSecs = rle.getRetryTime();
                log("Sleeping " + sleepSecs +
                            " seconds to recover from rate limit exception...");
                sleep(sleepSecs);
            }
        }
        return null;
    }

    /**
     * Return the list of subreddits which should not be limited by
     * poll creation throttling for preventing abuse. 
     **/
    public List<String> getUnlimitedSubreddits() {
        return _unlimitedSubreddits;
    }

    /**
     * Return bot owner.
     */
    public String getOwner() { return _owner; }

    /**
     *
     * Get the subreddit in which we should look for a suggestion thread
     * to post possible poll comments.
     *
     */
    public String getSuggestionSub() { return _suggestionSub; }


}
