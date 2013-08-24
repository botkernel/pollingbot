package com.jreddit.pollingbot;

import java.io.*;
import java.util.*;

import com.almworks.sqlite4java.*;
import com.jreddit.botkernel.*;

/**
 *
 * A poll option 
 *
 */
public class PollOption {

    private int _option;
    private String _value;

    public PollOption(int option, String value) {
        _option = option;
        _value = value;
    }

    public int getOption()      { return _option;    }
    public String getValue()    { return _value;     }

}
