/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.libuv;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import jdk.nashorn.internal.runtime.ConsString;

public final class StringUtils {

    private static Method leftMethod = null;
    private static Method rightMethod = null;
    static {
        try {
            leftMethod = ConsString.class.getDeclaredMethod("left");
            rightMethod = ConsString.class.getDeclaredMethod("right");
        } catch (NoSuchMethodException ignore) {
        }
    }

    public static Deque<String> parts(final ConsString root) {
        final Deque<String> parts = new ArrayDeque<>();
        if (!consStringHasLeftRight()) {
            return fallbackParts(root, parts);
        }

        try {
            return reflectiveParts(parts, root);
        } catch (InvocationTargetException | IllegalAccessException e) {
            return fallbackParts(root, parts);
        }
    }

    public static boolean consStringHasLeftRight() {
        return leftMethod != null && rightMethod != null;
    }

    private static Deque<String> fallbackParts(final ConsString root, final Deque<String> parts) {
        // fallback - return flattened contents as a single part
        parts.addFirst(root.toString());
        return parts;
    }

    public static Deque<String> reflectiveParts(final Deque<String> parts, final ConsString root)
            throws InvocationTargetException, IllegalAccessException {

        assert leftMethod != null;
        assert rightMethod != null;

        final CharSequence left = (CharSequence) leftMethod.invoke(root);
        final CharSequence right = (CharSequence) rightMethod.invoke(root);
        final Deque<CharSequence> stack = new ArrayDeque<>();

        stack.addFirst(left);
        CharSequence cs = right;

        // reuse the ConsString.flatten() algorithm, without actually flattening
        do {
            if (cs instanceof ConsString) {
                final ConsString cons = (ConsString) cs;
                stack.addFirst((CharSequence) leftMethod.invoke(cons));
                cs = (CharSequence) rightMethod.invoke(cons);
            } else {
                final String str = (String) cs;
                parts.offerFirst(str);
                cs = stack.isEmpty() ? null : stack.pollFirst();
            }
        } while (cs != null);

        return parts;
    }

    public static boolean hasMultiByte(final String str, final String encoding) {
        switch (encoding) {
            case "base64":
            case "hex":
            case "ucs2":
            case "utf16":
            case "utf16le":
            case "utf16be":
            case "utf32":
            case "utf32le":
            case "utf32be":
            case "ucs-2":
            case "utf-16":
            case "utf-16le":
            case "utf-16be":
            case "utf-32":
            case "utf32-le":
            case "utf32-be":
                return true;

            case "utf8":
            case "utf-8":
                final int length = str.length();
                for (int i=0; i < length; i++) {
                    if (str.charAt(i) > 0x7f) { // https://en.wikipedia.org/wiki/UTF-8
                        return true;
                    }
                }
                break;

            default:
                return true; // assume unknown encoding is multi-byte - slow but safe
        }
        // ascii, raw, binary, iso-8859-1 are not multi-byte
        return false;
    }
}
