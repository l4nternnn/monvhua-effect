package com.kuilunfuzhe.monvhua.features.textarea;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class RichTextFieldWidget extends ClickableWidget {
    private static final int BACKGROUND = 0xFF000000;
    private static final int BORDER = 0xFFA0A0A0;
    private static final int BORDER_FOCUSED = 0xFFFFFFFF;
    private static final int SELECTION = 0x8849D7FF;
    private static final int SELECTION_BORDER = 0xCC49D7FF;
    private static final int CURSOR = 0xFFD8D8D8;

    private final TextRenderer textRenderer;
    private String text = "";
    private int maxLength = 32;
    private int cursor;
    private int selectionEnd;
    private int firstCharacterIndex;
    private boolean draggingSelection;
    private Consumer<String> changedListener = value -> {
    };
    private Predicate<String> textPredicate = value -> true;

    public RichTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
    }

    public void setChangedListener(Consumer<String> changedListener) {
        this.changedListener = changedListener == null ? value -> {
        } : changedListener;
    }

    public void setTextPredicate(Predicate<String> textPredicate) {
        this.textPredicate = textPredicate == null ? value -> true : textPredicate;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        if (text.length() > this.maxLength) {
            setText(text.substring(0, this.maxLength));
        }
    }

    public void setText(String value) {
        String next = value == null ? "" : value;
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        if (!textPredicate.test(next)) {
            return;
        }
        text = next;
        cursor = MathHelper.clamp(cursor, 0, text.length());
        selectionEnd = MathHelper.clamp(selectionEnd, 0, text.length());
        updateFirstCharacterIndex(cursor);
        changedListener.accept(text);
    }

    public String getText() {
        return text;
    }

    public int getCursor() {
        return cursor;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public boolean hasSelection() {
        return cursor != selectionEnd;
    }

    public int selectionStart() {
        return Math.min(cursor, selectionEnd);
    }

    public int selectionStop() {
        return Math.max(cursor, selectionEnd);
    }

    public void setSelection(int start, int end) {
        cursor = MathHelper.clamp(start, 0, text.length());
        selectionEnd = MathHelper.clamp(end, 0, text.length());
        updateFirstCharacterIndex(selectionEnd);
    }

    public void setCursor(int cursor, boolean shiftDown) {
        this.cursor = MathHelper.clamp(cursor, 0, text.length());
        if (!shiftDown) {
            selectionEnd = this.cursor;
        }
        updateFirstCharacterIndex(this.cursor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setFocused(true);
        setCursor(characterIndexAt(mouseX), Screen.hasShiftDown());
        draggingSelection = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0 || !draggingSelection) {
            return false;
        }
        setCursor(characterIndexAt(mouseX), true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSelection) {
            draggingSelection = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) {
            return false;
        }
        if (Screen.isSelectAll(keyCode)) {
            cursor = text.length();
            selectionEnd = 0;
            updateFirstCharacterIndex(cursor);
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            copySelection();
            return true;
        }
        if (Screen.isCut(keyCode)) {
            copySelection();
            write("");
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            write(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        }
        boolean shift = Screen.hasShiftDown();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                erase(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                erase(1);
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                setCursor(Screen.hasControlDown() ? wordSkipPosition(-1) : cursor - 1, shift);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                setCursor(Screen.hasControlDown() ? wordSkipPosition(1) : cursor + 1, shift);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setCursor(0, shift);
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                setCursor(text.length(), shift);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || !StringHelper.isValidChar(chr)) {
            return false;
        }
        write(Character.toString(chr));
        return true;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND);
        int border = isFocused() ? BORDER_FOCUSED : BORDER;
        context.drawBorder(getX(), getY(), width, height, border);

        String visibleText = visibleText();
        int textX = textX();
        int textY = getY() + (height - textRenderer.fontHeight) / 2;
        renderSelection(context, textX, textY);
        context.drawText(textRenderer, visibleText, textX, textY, 0xFFE0E0E0, false);
        renderCursor(context, textX, textY);
    }

    private void renderSelection(DrawContext context, int textX, int textY) {
        if (!hasSelection()) {
            return;
        }
        int visibleEnd = firstCharacterIndex + visibleText().length();
        int start = MathHelper.clamp(selectionStart(), firstCharacterIndex, visibleEnd);
        int end = MathHelper.clamp(selectionStop(), firstCharacterIndex, visibleEnd);
        if (end <= start) {
            return;
        }
        int x1 = textX + textRenderer.getWidth(text.substring(firstCharacterIndex, start));
        int x2 = textX + textRenderer.getWidth(text.substring(firstCharacterIndex, end));
        context.fill(x1, getY() + 2, x2, getY() + height - 2, SELECTION);
        context.drawBorder(x1, getY() + 2, x2 - x1, height - 4, SELECTION_BORDER);
    }

    private void renderCursor(DrawContext context, int textX, int textY) {
        if (!isFocused() || (System.currentTimeMillis() / 300L) % 2L != 0L) {
            return;
        }
        int visibleEnd = firstCharacterIndex + visibleText().length();
        if (cursor < firstCharacterIndex || cursor > visibleEnd) {
            return;
        }
        int x = textX + textRenderer.getWidth(text.substring(firstCharacterIndex, cursor));
        context.fill(x, textY - 1, x + 1, textY + textRenderer.fontHeight + 1, CURSOR);
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        MinecraftClient.getInstance().keyboard.setClipboard(text.substring(selectionStart(), selectionStop()));
    }

    private void erase(int direction) {
        if (hasSelection()) {
            write("");
            return;
        }
        int target = MathHelper.clamp(cursor + direction, 0, text.length());
        replaceRange(Math.min(cursor, target), Math.max(cursor, target), "");
    }

    private void write(String value) {
        String cleaned = StringHelper.stripInvalidChars(value == null ? "" : value);
        int available = maxLength - text.length() + (selectionStop() - selectionStart());
        if (cleaned.length() > available) {
            cleaned = cleaned.substring(0, Math.max(0, available));
        }
        replaceRange(selectionStart(), selectionStop(), cleaned);
    }

    private void replaceRange(int start, int end, String replacement) {
        String next = text.substring(0, start) + replacement + text.substring(end);
        if (!textPredicate.test(next)) {
            return;
        }
        text = next;
        cursor = start + replacement.length();
        selectionEnd = cursor;
        updateFirstCharacterIndex(cursor);
        changedListener.accept(text);
    }

    private int characterIndexAt(double mouseX) {
        int localX = MathHelper.clamp((int) mouseX - textX(), 0, getInnerWidth());
        String visibleText = visibleText();
        String trimmed = textRenderer.trimToWidth(visibleText, localX);
        return MathHelper.clamp(firstCharacterIndex + trimmed.length(), 0, text.length());
    }

    private int wordSkipPosition(int direction) {
        int position = cursor;
        if (direction < 0) {
            while (position > 0 && text.charAt(position - 1) == ' ') {
                position--;
            }
            while (position > 0 && text.charAt(position - 1) != ' ') {
                position--;
            }
        } else {
            int length = text.length();
            while (position < length && text.charAt(position) != ' ') {
                position++;
            }
            while (position < length && text.charAt(position) == ' ') {
                position++;
            }
        }
        return position;
    }

    private void updateFirstCharacterIndex(int index) {
        index = MathHelper.clamp(index, 0, text.length());
        String beforeCursor = text.substring(firstCharacterIndex, index);
        while (textRenderer.getWidth(beforeCursor) > getInnerWidth() && firstCharacterIndex < index) {
            firstCharacterIndex++;
            beforeCursor = text.substring(firstCharacterIndex, index);
        }
        if (index < firstCharacterIndex) {
            firstCharacterIndex = index;
        }
        firstCharacterIndex = MathHelper.clamp(firstCharacterIndex, 0, text.length());
    }

    private String visibleText() {
        return textRenderer.trimToWidth(text.substring(firstCharacterIndex), getInnerWidth());
    }

    public int getInnerWidth() {
        return Math.max(0, width - 8);
    }

    private int textX() {
        return getX() + 4;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
