from markitdown import MarkItDown



def convert_to_markdown(file_path: str) -> str:
    """
    调用 MarkItDown 将文件转换为 Markdown
    :param file_path: 输入文件路径（支持.docx、.pdf 等）
    :param output_dir: 输出目录，None 则使用临时目录
    :return: 转换后的 Markdown 文件路径
    """
    # Set to True to enable plugins
    md = MarkItDown(enable_plugins=False)

    result = md.convert(file_path,keep_data_uris=True)

    return result.text_content



