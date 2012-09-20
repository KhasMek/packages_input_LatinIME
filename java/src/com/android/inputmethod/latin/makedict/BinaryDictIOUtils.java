/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput.FusionDictionaryBufferInterface;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

public class BinaryDictIOUtils {
    private static final boolean DBG = false;

    private static class Position {
        public static final int NOT_READ_GROUPCOUNT = -1;

        public int mAddress;
        public int mNumOfCharGroup;
        public int mPosition;
        public int mLength;

        public Position(int address, int length) {
            mAddress = address;
            mLength = length;
            mNumOfCharGroup = NOT_READ_GROUPCOUNT;
        }
    }

    /**
     * Tours all node without recursive call.
     */
    private static void readUnigramsAndBigramsBinaryInner(
            final FusionDictionaryBufferInterface buffer, final int headerSize,
            final Map<Integer, String> words, final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams,
            final FormatOptions formatOptions) {
        int[] pushedChars = new int[FormatSpec.MAX_WORD_LENGTH + 1];

        Stack<Position> stack = new Stack<Position>();
        int index = 0;

        Position initPos = new Position(headerSize, 0);
        stack.push(initPos);

        while (!stack.empty()) {
            Position p = stack.peek();

            if (DBG) {
                MakedictLog.d("read: address=" + p.mAddress + ", numOfCharGroup=" +
                        p.mNumOfCharGroup + ", position=" + p.mPosition + ", length=" + p.mLength);
            }

            if (buffer.position() != p.mAddress) buffer.position(p.mAddress);
            if (index != p.mLength) index = p.mLength;

            if (p.mNumOfCharGroup == Position.NOT_READ_GROUPCOUNT) {
                p.mNumOfCharGroup = BinaryDictInputOutput.readCharGroupCount(buffer);
                p.mAddress += BinaryDictInputOutput.getGroupCountSize(p.mNumOfCharGroup);
                p.mPosition = 0;
            }

            CharGroupInfo info = BinaryDictInputOutput.readCharGroup(buffer,
                    p.mAddress - headerSize, formatOptions);
            for (int i = 0; i < info.mCharacters.length; ++i) {
                pushedChars[index++] = info.mCharacters[i];
            }
            p.mPosition++;

            if (info.mFrequency != FusionDictionary.CharGroup.NOT_A_TERMINAL) { // found word
                words.put(info.mOriginalAddress, new String(pushedChars, 0, index));
                frequencies.put(info.mOriginalAddress, info.mFrequency);
                if (info.mBigrams != null) bigrams.put(info.mOriginalAddress, info.mBigrams);
            }

            if (p.mPosition == p.mNumOfCharGroup) {
                stack.pop();
            } else {
                // the node has more groups.
                p.mAddress = buffer.position();
            }

            if (BinaryDictInputOutput.hasChildrenAddress(info.mChildrenAddress)) {
                Position childrenPos = new Position(info.mChildrenAddress + headerSize, index);
                stack.push(childrenPos);
            }
        }
    }

    /**
     * Reads unigrams and bigrams from the binary file.
     * Doesn't make the memory representation of the dictionary.
     *
     * @param buffer the buffer to read.
     * @param words the map to store the address as a key and the word as a value.
     * @param frequencies the map to store the address as a key and the frequency as a value.
     * @param bigrams the map to store the address as a key and the list of address as a value.
     * @throws IOException
     * @throws UnsupportedFormatException
     */
    public static void readUnigramsAndBigramsBinary(final FusionDictionaryBufferInterface buffer,
            final Map<Integer, String> words, final Map<Integer, Integer> frequencies,
            final Map<Integer, ArrayList<PendingAttribute>> bigrams) throws IOException,
            UnsupportedFormatException {
        // Read header
        final FileHeader header = BinaryDictInputOutput.readHeader(buffer);
        readUnigramsAndBigramsBinaryInner(buffer, header.mHeaderSize, words, frequencies, bigrams,
                header.mFormatOptions);
    }
}