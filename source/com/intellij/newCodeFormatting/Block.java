package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;

import java.util.List;

public interface Block {
  TextRange getTextRange();

  List<Block> getSubBlocks();

  Wrap getWrap();

  Indent getChildIndent();

  Alignment getAlignment();

  SpaceProperty getSpaceProperty(Block child1, Block child2);

  Block getParent();

  boolean skipIndent(final Block parent);
}
