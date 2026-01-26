/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.gengzi.rag.agent.subagent.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件系统工具集
 *
 * <p>提供三个核心文件操作工具：</p>
 * <ul>
 *   <li><b>Read</b> - 读取文件内容，支持分页和截断</li>
 *   <li><b>Write</b> - 写入文件内容，覆盖已存在文件</li>
 *   <li><b>Edit</b> - 精确替换文件中的文本</li>
 * </ul>
 *
 * <h3>设计特点：</h3>
 * <ul>
 *   <li>安全性：Edit 工具确保唯一性匹配，避免误替换</li>
 *   <li>性能：Read 支持分页读取大文件</li>
 *   <li>可靠性：所有操作都有详细的错误处理</li>
 * </ul>
 *
 * @author Christian Tzolov
 */
public class FileSystemTools {

	/**
	 * 读取文件内容
	 *
	 * <p>功能特性：</p>
	 * <ul>
	 *   <li>支持分页读取（offset + limit）</li>
	 *   <li>自动截断超长行（超过 2000 字符）</li>
	 *   <li>返回带行号的格式化输出（类似 cat -n）</li>
	 *   <li>默认最多读取 2000 行</li>
	 * </ul>
	 *
	 * @param filePath 文件的绝对路径（必须使用绝对路径，不支持相对路径）
	 * @param offset 起始行号（可选，从 1 开始，默认为 1）
	 * @param limit 读取的最大行数（可选，默认为 2000）
	 * @param toolContext Spring AI 工具上下文
	 * @return 格式化的文件内容，包含行号和文件信息
	 */
	// @formatter:off
	@Tool(name = "Read", description = """
		Reads a file from the local filesystem. You can access any file directly by using this tool.
		Assume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.

		Usage:
		- The file_path parameter must be an absolute path, not a relative path
		- By default, it reads up to 2000 lines starting from the beginning of the file
		- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters
		- Any lines longer than 2000 characters will be truncated
		- Results are returned using cat -n format, with line numbers starting at 1
		- This tool allows Claude Code to read images (eg PNG, JPG, etc). When reading an image file the contents are presented visually as Claude Code is a multimodal LLM.
		- This tool can read PDF files (.pdf). PDFs are processed page by page, extracting both text and visual content for analysis.
		- This tool can read Jupyter notebooks (.ipynb files) and returns all cells with their outputs, combining code, text, and visualizations.
		- This tool can only read files, not directories. To read a directory, use an ls command via the Bash tool.
		- You can call multiple tools in a single response. It is always better to speculatively read multiple potentially useful files in parallel.
		- You will regularly be asked to read screenshots. If the user provides a path to a screenshot, ALWAYS use this tool to view the file at the path. This tool will work with all temporary file paths.
		- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents.
		""")
	public String read(
		@ToolParam(description = "The absolute path to the file to read") String filePath,
		@ToolParam(description = "The line number to start reading from. Only provide if the file is too large to read at once", required = false) Integer offset,
		@ToolParam(description = "The number of lines to read. Only provide if the file is too large to read at once.", required = false) Integer limit,
		ToolContext toolContext) { // @formatter:on

		try {
			File file = new File(filePath);

			// 验证文件存在性
			if (!file.exists()) {
				return "Error: File does not exist: " + filePath;
			}

			// 验证不是目录
			if (file.isDirectory()) {
				return "Error: Path is a directory, not a file: " + filePath;
			}

			// 设置默认参数值
			int startLine = offset != null ? offset : 1;  // 默认从第 1 行开始
			int maxLines = limit != null ? limit : 2000;  // 默认最多读取 2000 行

			if (startLine < 1) {
				startLine = 1;  // 起始行号不能小于 1
			}

			List<String> lines = new ArrayList<>();
			int currentLine = 0;  // 当前行号（从 1 开始）
			int linesRead = 0;    // 已读取的行数

			// 逐行读取文件
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = reader.readLine()) != null) {
					currentLine++;

					// 跳过 offset 之前的行
					if (currentLine < startLine) {
						continue;
					}

					// 达到最大行数则停止
					if (linesRead >= maxLines) {
						break;
					}

					// 截断超长行（超过 2000 字符）
					if (line.length() > 2000) {
						line = line.substring(0, 2000) + "... (line truncated)";
					}

					// 格式化输出：6 位右对齐行号 + tab + 内容
					lines.add(String.format("%6d\t%s", currentLine, line));
					linesRead++;
				}
			}

			// 处理空结果的情况
			if (lines.isEmpty()) {
				if (currentLine == 0) {
					return "File is empty: " + filePath;
				}
				else {
					return String.format("No lines to read. File has %d lines, but offset was %d", currentLine,
							startLine);
				}
			}

			// 构建返回结果
			StringBuilder result = new StringBuilder();
			result.append(String.format("File: %s\n", filePath));
			result.append(
					String.format("Showing lines %d-%d of %d\n\n", startLine, startLine + linesRead - 1, currentLine));

			for (String line : lines) {
				result.append(line).append("\n");
			}

			return result.toString();

		}
		catch (IOException e) {
			return "Error reading file: " + e.getMessage();
		}
	}

	/**
	 * 写入文件内容
	 *
	 * <p>功能特性：</p>
	 * <ul>
	 *   <li>覆盖已存在的文件</li>
	 *   <li>自动创建父目录</li>
	 *   <li>返回写入的字节数</li>
	 * </ul>
	 *
	 * @param filePath 文件的绝对路径
	 * @param content 要写入的内容
	 * @param toolContext Spring AI 工具上下文
	 * @return 操作结果消息
	 */
	// @formatter:off
	@Tool(name = "Write", description = """
		Writes a file to the local filesystem.

		Usage:
		- This tool will overwrite the existing file if there is one at the provided path.
		- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first.
		- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
		- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
		- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked.
		""")
	public String write(
		@ToolParam(description = "The absolute path to the file to write (must be absolute, not relative)") String filePath,
		@ToolParam(description = "The content to write to the file") String content,
		ToolContext toolContext) { // @formatter:on

		try {
			content = content != null ? content : "";

			Path path = Paths.get(filePath);
			File file = path.toFile();

			// 如果父目录不存在，自动创建
			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					return "Error: Failed to create parent directories for: " + filePath;
				}
			}

			// 检查文件是否已存在
			boolean fileExists = file.exists();

			// 写入内容到文件（覆盖模式）
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(content);
			}

			if (fileExists) {
				return String.format("Successfully overwrote file: %s (%d bytes)", filePath, content.length());
			}
			else {
				return String.format("Successfully created file: %s (%d bytes)", filePath, content.length());
			}

		}
		catch (IOException e) {
			return "Error writing file: " + e.getMessage();
		}
		catch (Exception e) {
			return "Error: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "Edit", description = """
		Performs exact string replacements in files.

		Usage:
		- You must use your `Read` tool at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file.
		- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: spaces + line number + tab. Everything after that tab is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string.
		- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.
		- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.
		- The edit will FAIL if `old_string` is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use `replace_all` to change every instance of `old_string`.
		- Use `replace_all` for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.
		""")
	/**
	 * 精确替换文件内容
	 *
	 * <p>功能特性：</p>
	 * <ul>
	 *   <li><b>唯一性检查：</b>确保 old_string 在文件中唯一，避免误替换</li>
	 *   <li><b>字面量替换：</b>不使用正则表达式，避免意外匹配</li>
	 *   <li><b>预览模式：</b>返回替换后的代码片段预览</li>
	 *   <li><b>全局替换：</b>支持 replace_all 参数替换所有匹配项</li>
	 * </ul>
	 *
	 * @param filePath 文件的绝对路径
	 * @param old_string 要替换的文本（必须唯一，除非使用 replace_all）
	 * @param new_string 替换后的文本（必须与 old_string 不同）
	 * @param replace_all 是否替换所有匹配项（默认 false，只替换第一个）
	 * @param toolContext Spring AI 工具上下文
	 * @return 操作结果和代码片段预览
	 */
	public String edit(
		@ToolParam(description = "The absolute path to the file to modify") String filePath,
		@ToolParam(description = "The text to replace") String old_string,
		@ToolParam(description = "The text to replace it with (must be different from old_string)") String new_string,
		@ToolParam(description = "Replace all occurences of old_string (default false)", required = false) Boolean replace_all,
		ToolContext toolContext) { // @formatter:on

		try {
			File file = new File(filePath);

			// 验证文件存在
			if (!file.exists()) {
				return "Error: File does not exist: " + filePath;
			}

			// 验证不是目录
			if (file.isDirectory()) {
				return "Error: Path is a directory, not a file: " + filePath;
			}

			// 验证 old_string 和 new_string 不同
			if (old_string.equals(new_string)) {
				return "Error: old_string and new_string must be different";
			}

			// 读取整个文件内容（保留精确的换行符）
			String originalContent;
			try {
				originalContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return "Error reading file content: " + e.getMessage();
			}

			// 统计 old_string 出现的次数
			int occurrences = countOccurrences(originalContent, old_string);

			if (occurrences == 0) {
				return "Error: old_string not found in file: " + filePath;
			}

			boolean replaceAll = Boolean.TRUE.equals(replace_all);

			// 如果不是全局替换且出现多次，拒绝执行（安全检查）
			if (!replaceAll && occurrences > 1) {
				return String.format(
						"Error: old_string appears %d times in the file. Either provide a larger string with more surrounding context to make it unique or use replace_all=true to change all instances.",
						occurrences);
			}

			// 执行替换操作
			String newContent;
			if (replaceAll) {
				// 替换所有匹配项（使用字面量替换，非正则）
				newContent = replaceAll(originalContent, old_string, new_string);
			}
			else {
				// 只替换第一个匹配项
				newContent = replaceFirst(originalContent, old_string, new_string);
			}

			// 将修改后的内容写回文件
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
				writer.write(newContent);
			}

			// 生成代码片段预览（显示修改位置周围的内容）
			String snippet = generateEditSnippet(newContent, new_string);

			// 返回格式化的响应（类似 Claude Code 的 Edit 工具格式）
			return String.format(
					"The file %s has been updated. Here's the result of running `cat -n` on a snippet of the edited file:\n%s",
					filePath, snippet);

		}
		catch (IOException e) {
			return "Error editing file: " + e.getMessage();
		}
	}

	/**
	 * 统计子字符串在文本中出现的次数
	 * @param text 文本
	 * @param substring 要查找的子字符串
	 * @return 出现的次数
	 */
	// Helper method to count occurrences of a substring
	private int countOccurrences(String text, String substring) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(substring, index)) != -1) {
			count++;
			index += substring.length();
		}
		return count;
	}

	// Helper method to replace first occurrence
	private String replaceFirst(String text, String old_string, String new_string) {
		int index = text.indexOf(old_string);
		if (index == -1) {
			return text;
		}
		return text.substring(0, index) + new_string + text.substring(index + old_string.length());
	}

	// Helper method to replace all occurrences (literal, not regex)
	private String replaceAll(String text, String old_string, String new_string) {
		StringBuilder result = new StringBuilder();
		int index = 0;
		int lastIndex = 0;

		while ((index = text.indexOf(old_string, lastIndex)) != -1) {
			result.append(text, lastIndex, index);
			result.append(new_string);
			lastIndex = index + old_string.length();
		}
		result.append(text.substring(lastIndex));

		return result.toString();
	}

	/**
	 * Generates a formatted snippet of the file showing context around the edited
	 * section. Matches Claude Code's Edit tool output format with line numbers and arrow
	 * separator.
	 * @param fileContent the complete file content after editing
	 * @param newString the new string that was inserted (used to find the edit location)
	 * @return formatted snippet with line numbers
	 */
	private String generateEditSnippet(String fileContent, String newString) {
		String[] lines = fileContent.split("\n", -1);

		// Find the line where the new content appears
		int editStartLine = -1;
		int editEndLine = -1;

		// Split new_string into lines to find where it appears in the file
		String[] newLines = newString.split("\n", -1);

		// Search for the first line of the new content
		for (int i = 0; i < lines.length; i++) {
			if (newLines.length > 0 && lines[i].contains(newLines[0])) {
				// Check if subsequent lines match (for multi-line edits)
				boolean matches = true;
				for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
					if (!lines[i + j].contains(newLines[j])) {
						matches = false;
						break;
					}
				}
				if (matches) {
					editStartLine = i;
					editEndLine = i + newLines.length - 1;
					break;
				}
			}
		}

		// If we didn't find the edit location, show the beginning of the file
		if (editStartLine == -1) {
			editStartLine = 0;
			editEndLine = Math.min(10, lines.length - 1);
		}

		// Show context: ~5 lines before and ~5 lines after the edit
		int contextBefore = 5;
		int contextAfter = 5;
		int startLine = Math.max(0, editStartLine - contextBefore);
		int endLine = Math.min(lines.length - 1, editEndLine + contextAfter);

		// Build the snippet with line numbers (1-indexed, right-aligned with arrow)
		StringBuilder snippet = new StringBuilder();
		for (int i = startLine; i <= endLine; i++) {
			// Line numbers are 1-indexed and right-aligned to 6 characters
			snippet.append(String.format("%6d→%s", i + 1, lines[i]));
			if (i < endLine) {
				snippet.append("\n");
			}
		}

		return snippet.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		public FileSystemTools build() {
			return new FileSystemTools();
		}
	}

}
