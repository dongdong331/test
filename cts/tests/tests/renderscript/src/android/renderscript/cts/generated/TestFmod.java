/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Don't edit this file!  It is auto-generated by frameworks/rs/api/generate.sh.

package android.renderscript.cts;

import android.renderscript.Allocation;
import android.renderscript.RSRuntimeException;
import android.renderscript.Element;
import android.renderscript.cts.Target;

import java.util.Arrays;

public class TestFmod extends RSBaseCompute {

    private ScriptC_TestFmod script;
    private ScriptC_TestFmodRelaxed scriptRelaxed;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        script = new ScriptC_TestFmod(mRS);
        scriptRelaxed = new ScriptC_TestFmodRelaxed(mRS);
    }

    @Override
    protected void tearDown() throws Exception {
        script.destroy();
        scriptRelaxed.destroy();
        super.tearDown();
    }

    public class ArgumentsFloatFloatFloat {
        public float inNumerator;
        public float inDenominator;
        public Target.Floaty out;
    }

    private void checkFmodFloatFloatFloat() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 1, 0xed70b65ddcc790e8l, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 1, 0xeff8dc0a04b044e1l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodFloatFloatFloat(inNumerator, out);
            verifyResultsFmodFloatFloatFloat(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloatFloatFloat: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodFloatFloatFloat(inNumerator, out);
            verifyResultsFmodFloatFloatFloat(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloatFloatFloat: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodFloatFloatFloat(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        float[] arrayInNumerator = new float[INPUTSIZE * 1];
        Arrays.fill(arrayInNumerator, (float) 42);
        inNumerator.copyTo(arrayInNumerator);
        float[] arrayInDenominator = new float[INPUTSIZE * 1];
        Arrays.fill(arrayInDenominator, (float) 42);
        inDenominator.copyTo(arrayInDenominator);
        float[] arrayOut = new float[INPUTSIZE * 1];
        Arrays.fill(arrayOut, (float) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 1 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inNumerator = arrayInNumerator[i];
                args.inDenominator = arrayInDenominator[i];
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 1 + j]);
                        if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodFloatFloatFloat" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodFloat2Float2Float2() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 2, 0x84bcef91ebd95a82l, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 2, 0xb582050adc295e2bl, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodFloat2Float2Float2(inNumerator, out);
            verifyResultsFmodFloat2Float2Float2(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat2Float2Float2: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodFloat2Float2Float2(inNumerator, out);
            verifyResultsFmodFloat2Float2Float2(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat2Float2Float2: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodFloat2Float2Float2(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        float[] arrayInNumerator = new float[INPUTSIZE * 2];
        Arrays.fill(arrayInNumerator, (float) 42);
        inNumerator.copyTo(arrayInNumerator);
        float[] arrayInDenominator = new float[INPUTSIZE * 2];
        Arrays.fill(arrayInDenominator, (float) 42);
        inDenominator.copyTo(arrayInDenominator);
        float[] arrayOut = new float[INPUTSIZE * 2];
        Arrays.fill(arrayOut, (float) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 2 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inNumerator = arrayInNumerator[i * 2 + j];
                args.inDenominator = arrayInDenominator[i * 2 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 2 + j]);
                        if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodFloat2Float2Float2" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodFloat3Float3Float3() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 3, 0x604b98cb8ea54683l, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 3, 0x7ee64653af04f164l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodFloat3Float3Float3(inNumerator, out);
            verifyResultsFmodFloat3Float3Float3(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat3Float3Float3: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodFloat3Float3Float3(inNumerator, out);
            verifyResultsFmodFloat3Float3Float3(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat3Float3Float3: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodFloat3Float3Float3(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        float[] arrayInNumerator = new float[INPUTSIZE * 4];
        Arrays.fill(arrayInNumerator, (float) 42);
        inNumerator.copyTo(arrayInNumerator);
        float[] arrayInDenominator = new float[INPUTSIZE * 4];
        Arrays.fill(arrayInDenominator, (float) 42);
        inDenominator.copyTo(arrayInDenominator);
        float[] arrayOut = new float[INPUTSIZE * 4];
        Arrays.fill(arrayOut, (float) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 3 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inNumerator = arrayInNumerator[i * 4 + j];
                args.inDenominator = arrayInDenominator[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 4 + j]);
                        if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodFloat3Float3Float3" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodFloat4Float4Float4() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 4, 0x3bda420531713284l, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 4, 0x484a879c81e0849dl, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodFloat4Float4Float4(inNumerator, out);
            verifyResultsFmodFloat4Float4Float4(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat4Float4Float4: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodFloat4Float4Float4(inNumerator, out);
            verifyResultsFmodFloat4Float4Float4(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodFloat4Float4Float4: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodFloat4Float4Float4(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        float[] arrayInNumerator = new float[INPUTSIZE * 4];
        Arrays.fill(arrayInNumerator, (float) 42);
        inNumerator.copyTo(arrayInNumerator);
        float[] arrayInDenominator = new float[INPUTSIZE * 4];
        Arrays.fill(arrayInDenominator, (float) 42);
        inDenominator.copyTo(arrayInDenominator);
        float[] arrayOut = new float[INPUTSIZE * 4];
        Arrays.fill(arrayOut, (float) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 4 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloatFloat args = new ArgumentsFloatFloatFloat();
                args.inNumerator = arrayInNumerator[i * 4 + j];
                args.inDenominator = arrayInDenominator[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 4 + j]);
                        if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodFloat4Float4Float4" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    public class ArgumentsHalfHalfHalf {
        public short inNumerator;
        public double inNumeratorDouble;
        public short inDenominator;
        public double inDenominatorDouble;
        public Target.Floaty out;
    }

    private void checkFmodHalfHalfHalf() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 1, 0xae3e08be9b7bf563l, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 1, 0x7af0d8cb699a0144l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 1), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodHalfHalfHalf(inNumerator, out);
            verifyResultsFmodHalfHalfHalf(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalfHalfHalf: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 1), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodHalfHalfHalf(inNumerator, out);
            verifyResultsFmodHalfHalfHalf(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalfHalfHalf: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodHalfHalfHalf(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        short[] arrayInNumerator = new short[INPUTSIZE * 1];
        Arrays.fill(arrayInNumerator, (short) 42);
        inNumerator.copyTo(arrayInNumerator);
        short[] arrayInDenominator = new short[INPUTSIZE * 1];
        Arrays.fill(arrayInDenominator, (short) 42);
        inDenominator.copyTo(arrayInDenominator);
        short[] arrayOut = new short[INPUTSIZE * 1];
        Arrays.fill(arrayOut, (short) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 1 ; j++) {
                // Extract the inputs.
                ArgumentsHalfHalfHalf args = new ArgumentsHalfHalfHalf();
                args.inNumerator = arrayInNumerator[i];
                args.inNumeratorDouble = Float16Utils.convertFloat16ToDouble(args.inNumerator);
                args.inDenominator = arrayInDenominator[i];
                args.inDenominatorDouble = Float16Utils.convertFloat16ToDouble(args.inDenominator);
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.HALF, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 1 + j]))) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 1 + j]);
                        message.append("\n");
                        message.append("Actual   output out (in double): ");
                        appendVariableToMessage(message, Float16Utils.convertFloat16ToDouble(arrayOut[i * 1 + j]));
                        if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 1 + j]))) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodHalfHalfHalf" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodHalf2Half2Half2() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 2, 0xb52d0395e67736bdl, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 2, 0x8f0295c7fa55044el, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 2), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodHalf2Half2Half2(inNumerator, out);
            verifyResultsFmodHalf2Half2Half2(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf2Half2Half2: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 2), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodHalf2Half2Half2(inNumerator, out);
            verifyResultsFmodHalf2Half2Half2(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf2Half2Half2: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodHalf2Half2Half2(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        short[] arrayInNumerator = new short[INPUTSIZE * 2];
        Arrays.fill(arrayInNumerator, (short) 42);
        inNumerator.copyTo(arrayInNumerator);
        short[] arrayInDenominator = new short[INPUTSIZE * 2];
        Arrays.fill(arrayInDenominator, (short) 42);
        inDenominator.copyTo(arrayInDenominator);
        short[] arrayOut = new short[INPUTSIZE * 2];
        Arrays.fill(arrayOut, (short) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 2 ; j++) {
                // Extract the inputs.
                ArgumentsHalfHalfHalf args = new ArgumentsHalfHalfHalf();
                args.inNumerator = arrayInNumerator[i * 2 + j];
                args.inNumeratorDouble = Float16Utils.convertFloat16ToDouble(args.inNumerator);
                args.inDenominator = arrayInDenominator[i * 2 + j];
                args.inDenominatorDouble = Float16Utils.convertFloat16ToDouble(args.inDenominator);
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.HALF, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 2 + j]))) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 2 + j]);
                        message.append("\n");
                        message.append("Actual   output out (in double): ");
                        appendVariableToMessage(message, Float16Utils.convertFloat16ToDouble(arrayOut[i * 2 + j]));
                        if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 2 + j]))) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodHalf2Half2Half2" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodHalf3Half3Half3() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 3, 0x6fcbd4a531e9672cl, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 3, 0x74168d3fe614d605l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 3), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodHalf3Half3Half3(inNumerator, out);
            verifyResultsFmodHalf3Half3Half3(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf3Half3Half3: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 3), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodHalf3Half3Half3(inNumerator, out);
            verifyResultsFmodHalf3Half3Half3(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf3Half3Half3: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodHalf3Half3Half3(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        short[] arrayInNumerator = new short[INPUTSIZE * 4];
        Arrays.fill(arrayInNumerator, (short) 42);
        inNumerator.copyTo(arrayInNumerator);
        short[] arrayInDenominator = new short[INPUTSIZE * 4];
        Arrays.fill(arrayInDenominator, (short) 42);
        inDenominator.copyTo(arrayInDenominator);
        short[] arrayOut = new short[INPUTSIZE * 4];
        Arrays.fill(arrayOut, (short) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 3 ; j++) {
                // Extract the inputs.
                ArgumentsHalfHalfHalf args = new ArgumentsHalfHalfHalf();
                args.inNumerator = arrayInNumerator[i * 4 + j];
                args.inNumeratorDouble = Float16Utils.convertFloat16ToDouble(args.inNumerator);
                args.inDenominator = arrayInDenominator[i * 4 + j];
                args.inDenominatorDouble = Float16Utils.convertFloat16ToDouble(args.inDenominator);
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.HALF, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]))) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 4 + j]);
                        message.append("\n");
                        message.append("Actual   output out (in double): ");
                        appendVariableToMessage(message, Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]));
                        if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]))) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodHalf3Half3Half3" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    private void checkFmodHalf4Half4Half4() {
        Allocation inNumerator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 4, 0x2a6aa5b47d5b979bl, false);
        Allocation inDenominator = createRandomAllocation(mRS, Element.DataType.FLOAT_16, 4, 0x592a84b7d1d4a7bcl, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 4), INPUTSIZE);
            script.set_gAllocInDenominator(inDenominator);
            script.forEach_testFmodHalf4Half4Half4(inNumerator, out);
            verifyResultsFmodHalf4Half4Half4(inNumerator, inDenominator, out, false);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf4Half4Half4: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_16, 4), INPUTSIZE);
            scriptRelaxed.set_gAllocInDenominator(inDenominator);
            scriptRelaxed.forEach_testFmodHalf4Half4Half4(inNumerator, out);
            verifyResultsFmodHalf4Half4Half4(inNumerator, inDenominator, out, true);
            out.destroy();
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testFmodHalf4Half4Half4: " + e.toString());
        }
        inNumerator.destroy();
        inDenominator.destroy();
    }

    private void verifyResultsFmodHalf4Half4Half4(Allocation inNumerator, Allocation inDenominator, Allocation out, boolean relaxed) {
        short[] arrayInNumerator = new short[INPUTSIZE * 4];
        Arrays.fill(arrayInNumerator, (short) 42);
        inNumerator.copyTo(arrayInNumerator);
        short[] arrayInDenominator = new short[INPUTSIZE * 4];
        Arrays.fill(arrayInDenominator, (short) 42);
        inDenominator.copyTo(arrayInDenominator);
        short[] arrayOut = new short[INPUTSIZE * 4];
        Arrays.fill(arrayOut, (short) 42);
        out.copyTo(arrayOut);
        StringBuilder message = new StringBuilder();
        boolean errorFound = false;
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 4 ; j++) {
                // Extract the inputs.
                ArgumentsHalfHalfHalf args = new ArgumentsHalfHalfHalf();
                args.inNumerator = arrayInNumerator[i * 4 + j];
                args.inNumeratorDouble = Float16Utils.convertFloat16ToDouble(args.inNumerator);
                args.inDenominator = arrayInDenominator[i * 4 + j];
                args.inDenominatorDouble = Float16Utils.convertFloat16ToDouble(args.inDenominator);
                // Figure out what the outputs should have been.
                Target target = new Target(Target.FunctionType.NORMAL, Target.ReturnType.HALF, relaxed);
                CoreMathVerifier.computeFmod(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]))) {
                    valid = false;
                }
                if (!valid) {
                    if (!errorFound) {
                        errorFound = true;
                        message.append("Input inNumerator: ");
                        appendVariableToMessage(message, args.inNumerator);
                        message.append("\n");
                        message.append("Input inDenominator: ");
                        appendVariableToMessage(message, args.inDenominator);
                        message.append("\n");
                        message.append("Expected output out: ");
                        appendVariableToMessage(message, args.out);
                        message.append("\n");
                        message.append("Actual   output out: ");
                        appendVariableToMessage(message, arrayOut[i * 4 + j]);
                        message.append("\n");
                        message.append("Actual   output out (in double): ");
                        appendVariableToMessage(message, Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]));
                        if (!args.out.couldBe(Float16Utils.convertFloat16ToDouble(arrayOut[i * 4 + j]))) {
                            message.append(" FAIL");
                        }
                        message.append("\n");
                        message.append("Errors at");
                    }
                    message.append(" [");
                    message.append(Integer.toString(i));
                    message.append(", ");
                    message.append(Integer.toString(j));
                    message.append("]");
                }
            }
        }
        assertFalse("Incorrect output for checkFmodHalf4Half4Half4" +
                (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), errorFound);
    }

    public void testFmod() {
        checkFmodFloatFloatFloat();
        checkFmodFloat2Float2Float2();
        checkFmodFloat3Float3Float3();
        checkFmodFloat4Float4Float4();
        checkFmodHalfHalfHalf();
        checkFmodHalf2Half2Half2();
        checkFmodHalf3Half3Half3();
        checkFmodHalf4Half4Half4();
    }
}
