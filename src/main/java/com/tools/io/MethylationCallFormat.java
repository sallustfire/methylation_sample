package com.tools.io;

import java.util.regex.Pattern;

public interface MethylationCallFormat {
  public final String FIELD_DELIMITER = "\t";
  public final Pattern FIELD_PATTERN = Pattern.compile(FIELD_DELIMITER);

  public final String PRAGMA = "#";
  public final String FORMAT_IDENTIFIER = "###methylcf";
  public final String CONTROL = "#control";
  public final String SEQUENCE = "#seq";
}
