import requests
from bs4 import BeautifulSoup
import re
import time
import os
from PIL import Image
import io
import pytesseract
# 尝试自动检测并设置tesseract的安装路径
tesseract_paths = [
    r'C:\Program Files\Tesseract-OCR\tesseract.exe',
    r'C:\Program Files (x86)\Tesseract-OCR\tesseract.exe',
    r'D:\Program Files\Tesseract-OCR\tesseract.exe',
    r'D:\Program Files (x86)\Tesseract-OCR\tesseract.exe',
    r'E:\anzhuang\tessocr\tesseract.exe'
]

# 找到第一个存在的tesseract路径
for path in tesseract_paths:
    if os.path.exists(path):
        pytesseract.pytesseract.tesseract_cmd = path
        print(f"使用tesseract路径: {path}")
        break

# 如果没有找到，提示用户
try:
    # 测试tesseract是否可用
    pytesseract.get_tesseract_version()
    print("Tesseract OCR引擎已准备就绪")
except Exception as e:
    print(f"警告: 无法初始化Tesseract OCR引擎: {str(e)}")
    print("请确保tesseract已正确安装，并检查安装路径")
    print("如果tesseract安装在自定义路径，请手动修改代码中的pytesseract.pytesseract_cmd设置")

import shutil

class GBStdSpider:
    def __init__(self):
        # 增强的headers，模拟真实浏览器
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Cache-Control': 'max-age=0'
        }
        self.base_url = 'https://openstd.samr.gov.cn/bzgk/gb/std_list_type?r=0.25200845908185987&page=1&pageSize=50&p.p1=2&p.p90=circulation_date&p.p91=desc'
        self.download_base_url = 'http://c.gb688.cn/bzgk/gb/showGb?type=download&hcno='
        self.verify_code_url = 'http://c.gb688.cn/bzgk/gb/verifyCode'
        self.save_dir = 'F:\\baidu\\document'
        # 初始化会话
        self.session = requests.Session()
        self.session.headers.update(self.headers)
    
    def fetch_page(self, url=None):
        """获取网页内容，添加会话和重试机制"""
        if url is None:
            url = self.base_url
        
        max_retries = 3
        for retry in range(max_retries):
            try:
                response = self.session.get(url, timeout=30)
                response.encoding = 'utf-8'
                
                if response.status_code == 200:
                    print(f"成功获取网页内容 (状态码: 200)")
                    # 保存获取的内容用于调试
                    with open('debug_page.html', 'w', encoding='utf-8') as f:
                        f.write(response.text)
                    return response.text
                else:
                    print(f"请求失败，状态码: {response.status_code}")
                    if retry < max_retries - 1:
                        time.sleep(2)
            except Exception as e:
                print(f"获取网页内容时出错: {str(e)}")
                if retry < max_retries - 1:
                    time.sleep(2)
        
        return None
    
    def get_captcha(self, hcno):
        """获取验证码图片并识别"""
        captcha_url = f"{self.download_base_url}{hcno}"
        try:
            # 首先访问下载页面，这样才能获取验证码
            print(f"访问下载页面: {captcha_url}")
            response = self.session.get(captcha_url, timeout=30)
            response.encoding = 'utf-8'
            
            if response.status_code == 200:
                # 保存页面内容用于调试
                with open('download_page.html', 'w', encoding='utf-8') as f:
                    f.write(response.text)
                print("下载页面已保存到 download_page.html")
                
                # 打印页面的前500个字符以便调试
                print("\n页面内容预览:")
                print(response.text[:500])
                print("...")
                
                # 解析HTML查找验证码图片URL
                soup = BeautifulSoup(response.text, 'html.parser')
                
                # 尝试查找所有img标签
                all_images = soup.find_all('img')
                print(f"\n找到 {len(all_images)} 个图片标签")
                
                # 特别查找类名为verifyCode的验证码图片
                captcha_image = soup.find('img', class_='verifyCode')
                if captcha_image:
                    captcha_src = captcha_image.get('src', '')
                    print(f"找到验证码图片，src: '{captcha_src}'")
                    
                    # 构建完整的验证码URL
                    if captcha_src:
                        if captcha_src.startswith('http'):
                            captcha_image_url = captcha_src
                        else:
                            # 验证码URL可能是相对路径，需要补全
                            captcha_image_url = f"http://c.gb688.cn/c/{captcha_src}" 
                        
                        # 尝试获取验证码图片
                        try:
                            test_response = self.session.get(captcha_image_url, timeout=10)
                            content_type = test_response.headers.get('Content-Type', '')
                            print(f"测试验证码URL: {captcha_image_url}, 状态码: {test_response.status_code}, 内容类型: {content_type}")
                            if test_response.status_code == 200 and 'image' in content_type:
                                print(f"成功获取到验证码图片")
                                
                                # 保存验证码图片
                                with open('captcha.png', 'wb') as f:
                                    f.write(test_response.content)
                                print("验证码图片已保存到 captcha.png")
                                
                                # 预处理和OCR识别
                                image = Image.open(io.BytesIO(test_response.content))
                                # 预处理图片以提高识别率
                                # 1. 转为灰度图
                                image = image.convert('L')
                                
                                # 2. 应用自适应阈值进行二值化
                                from PIL import ImageOps
                                image = ImageOps.autocontrast(image, cutoff=2)
                                
                                # 3. 应用高斯模糊去除噪点
                                from PIL import ImageFilter
                                image = image.filter(ImageFilter.GaussianBlur(radius=0.5))
                                
                                # 4. 再次应用阈值
                                threshold = 140
                                image = image.point(lambda x: 255 if x > threshold else 0, '1')
                                
                                # 5. 保存处理后的图片用于调试
                                processed_captcha_path = 'processed_captcha.png'
                                image.save(processed_captcha_path)
                                print(f"处理后的验证码图片已保存到: {processed_captcha_path}")
                                
                                # 6. 尝试多种OCR配置
                                configs = [
                                    '--psm 6',  # 假设是单个均匀块文本
                                    '--psm 8',  # 假设是单个词
                                    '--psm 10', # 假设是单个字符
                                    '--psm 13'  # 假设是原始行
                                ]
                                
                                best_captcha = ""
                                for config in configs:
                                    try:
                                        captcha_text = pytesseract.image_to_string(image, lang='eng', config=config)
                                        # 清理识别结果，只保留字母和数字
                                        captcha_text = re.sub(r'[^A-Za-z0-9]', '', captcha_text).strip()
                                        print(f"OCR配置 {config} 识别结果: {captcha_text}")
                                        # 选择长度最合理的结果（通常验证码长度在4-6之间）
                                        if 4 <= len(captcha_text) <= 6 and len(captcha_text) > len(best_captcha):
                                            best_captcha = captcha_text
                                    except Exception as ocr_error:
                                        print(f"OCR配置 {config} 失败: {str(ocr_error)}")
                                
                                captcha_text = best_captcha
                                print(f"识别到的验证码: {captcha_text}")
                                return captcha_text
                        except Exception as e:
                            print(f"获取验证码时出错: {str(e)}")
                
                # 打印所有图片标签信息
                for i, img in enumerate(all_images):
                    src = img.get('src', '')
                    alt = img.get('alt', '')
                    print(f"图片 {i+1}: src='{src}', alt='{alt}'")
                
                # 查找所有可能包含验证码的标签
                captcha_elements = soup.find_all(lambda tag: tag.name and ('验证码' in str(tag) or 'verify' in str(tag).lower()))
                print(f"\n找到 {len(captcha_elements)} 个可能与验证码相关的元素")
                for i, elem in enumerate(captcha_elements):
                    print(f"元素 {i+1}: {elem}")
                
                # 尝试提取所有表单，可能包含验证码提交
                forms = soup.find_all('form')
                print(f"\n找到 {len(forms)} 个表单")
                for i, form in enumerate(forms):
                    action = form.get('action', '无action')
                    print(f"表单 {i+1}: action='{action}'")
                    # 打印表单内的输入字段
                    inputs = form.find_all('input')
                    for inp in inputs:
                        name = inp.get('name', '无name')
                        type_ = inp.get('type', '无type')
                        print(f"  输入字段: name='{name}', type='{type_}'")
                
                # 尝试更多可能的验证码URL，特别是用户提供的格式
                possible_urls = [
                    'http://c.gb688.cn/bzgk/gb/gc?_=' + str(int(time.time() * 1000)),  # 用户提供的验证码URL格式
                ]
                
                captcha_image_url = None
                for url in possible_urls:
                    try:
                        # 使用会话对象发送请求，确保cookie一致性
                        test_response = self.session.get(url, timeout=10)
                        content_type = test_response.headers.get('Content-Type', '')
                        print(f"测试验证码URL: {url}, 状态码: {test_response.status_code}, 内容类型: {content_type}")
                        if test_response.status_code == 200 and 'image' in content_type:
                            captcha_image_url = url
                            print(f"测试成功，使用验证码URL: {url}")
                            break
                    except Exception as test_error:
                        print(f"测试URL {url} 时出错: {str(test_error)}")
                
                if captcha_image_url:
                    # 获取验证码图片
                    captcha_response = self.session.get(captcha_image_url, timeout=30)
                    
                    if captcha_response.status_code == 200 and 'image' in captcha_response.headers.get('Content-Type', ''):
                        # 保存验证码图片
                        with open('captcha.png', 'wb') as f:
                            f.write(captcha_response.content)
                        print("验证码图片已保存到 captcha.png")
                        
                        try:
                            # 尝试使用pytesseract识别验证码
                            image = Image.open(io.BytesIO(captcha_response.content))
                            # 预处理图片以提高识别率
                            # 1. 转为灰度图
                            image = image.convert('L')
                            
                            # 2. 应用自适应阈值进行二值化
                            from PIL import ImageOps
                            image = ImageOps.autocontrast(image, cutoff=2)
                            
                            # 3. 应用高斯模糊去除噪点
                            import numpy as np
                            from PIL import ImageFilter
                            image = image.filter(ImageFilter.GaussianBlur(radius=0.5))
                            
                            # 4. 再次应用阈值
                            threshold = 140
                            image = image.point(lambda x: 255 if x > threshold else 0, '1')
                            
                            # 5. 保存处理后的图片用于调试
                            processed_captcha_path = 'processed_captcha.png'
                            image.save(processed_captcha_path)
                            print(f"处理后的验证码图片已保存到: {processed_captcha_path}")
                            
                            # 6. 尝试多种OCR配置
                            configs = [
                                '--psm 6',  # 假设是单个均匀块文本
                                '--psm 8',  # 假设是单个词
                                '--psm 10', # 假设是单个字符
                                '--psm 13'  # 假设是原始行
                            ]
                            
                            best_captcha = ""
                            for config in configs:
                                try:
                                    captcha_text = pytesseract.image_to_string(image, lang='eng', config=config)
                                    # 清理识别结果，只保留字母和数字
                                    captcha_text = re.sub(r'[^A-Za-z0-9]', '', captcha_text).strip()
                                    print(f"OCR配置 {config} 识别结果: {captcha_text}")
                                    # 选择长度最合理的结果（通常验证码长度在4-6之间）
                                    if 4 <= len(captcha_text) <= 6 and len(captcha_text) > len(best_captcha):
                                        best_captcha = captcha_text
                                except Exception as ocr_error:
                                    print(f"OCR配置 {config} 失败: {str(ocr_error)}")
                            
                            captcha_text = best_captcha
                            
                            print(f"识别到的验证码: {captcha_text}")
                            return captcha_text
                        except Exception as img_error:
                            print(f"验证码识别出错: {str(img_error)}")
                            return None
                
                # 由于在自动化环境中难以准确识别验证码URL，我们将尝试直接调用验证接口
                return None
            else:
                print(f"访问下载页面失败，状态码: {response.status_code}")
                print(f"响应头: {dict(response.headers)}")
                print(f"响应内容: {response.text[:200]}...")
                return None
        except Exception as e:
            print(f"获取验证码时出错: {str(e)}")
            import traceback
            print(traceback.format_exc())
            return None
    
    def verify_captcha(self, hcno, captcha):
        """验证验证码并获取下载链接"""
        try:
            # 构建验证请求参数，根据用户提供的信息，只使用verifyCode参数
            # 格式为application/x-www-form-urlencoded
            data = {
                'verifyCode': captcha
            }
            
            print(f"发送验证请求到: {self.verify_code_url}")
            print(f"请求参数 (application/x-www-form-urlencoded): {data}")
            
            # 确保会话中包含正确的cookie和Referer
            # 获取当前会话的所有cookie
            cookies = self.session.cookies.get_dict()
            print(f"当前会话cookies: {cookies}")
            
            # 发送POST请求验证，确保使用正确的content-type
            response = self.session.post(
                self.verify_code_url, 
                data=data, 
                headers={
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Referer': f"http://c.gb688.cn/bzgk/gb/showGb?type=download&hcno={hcno}"
                },
                timeout=30
            )
            
            print(f"验证响应状态码: {response.status_code}")
            print(f"响应头: {dict(response.headers)}")
            print(f"响应内容长度: {len(response.text)}")
            print(f"响应内容: {response.text}")
            
            # 处理响应
            if response.status_code == 200:
                response_text = response.text.strip()
                
                # 特殊处理：根据web_reference，验证失败可能返回'error'
                if response_text.lower() == 'error':
                    print("验证码错误或验证失败")
                    return None
                
                # 如果响应是下载链接
                elif response_text.startswith('http'):
                    print(f"直接返回下载链接: {response_text}")
                    return response_text
                
                # 如果可能是相对路径
                elif response_text.startswith('/'):
                    full_url = f"http://c.gb688.cn{response_text}"
                    print(f"构建完整下载链接: {full_url}")
                    return full_url
                
                # 如果验证成功，尝试直接构造下载链接
                elif response_text.lower() in ['success', 'ok', 'true'] or response_text:
                    print(f"验证码验证响应: {response_text}")
                    # 尝试直接构造下载链接
                    direct_download_link = f"http://c.gb688.cn/bzgk/gb/viewGb?hcno={hcno}"
                    print(f"尝试直接构造下载链接: {direct_download_link}")
                    return direct_download_link
                
                else:
                    print(f"未知的验证响应: {response_text}")
            else:
                print(f"验证请求失败，状态码: {response.status_code}")
            
            return None
        except Exception as e:
            print(f"验证验证码时出错: {str(e)}")
            import traceback
            print(traceback.format_exc())
            return None
    
    def download_file(self, download_link, hcno):
        """下载文件到指定目录"""
        # 确保保存目录存在
        if not os.path.exists(self.save_dir):
            os.makedirs(self.save_dir)
        
        try:
            print(f"准备下载文件，链接: {download_link}")
            
            # 设置默认文件名
            filename = f"{hcno}.pdf"  # 假设下载的是PDF文件
            file_path = os.path.join(self.save_dir, filename)
            
            # 发送请求下载文件
            response = self.session.get(download_link, stream=True, timeout=60)
            
            print(f"下载响应状态码: {response.status_code}")
            print(f"下载响应头: {dict(response.headers)}")
            
            if response.status_code == 200:
                # 检查文件类型
                content_type = response.headers.get('Content-Type', '')
                print(f"内容类型: {content_type}")
                
                # 尝试从Content-Disposition获取文件名
                if 'Content-Disposition' in response.headers:
                    content_disposition = response.headers['Content-Disposition']
                    print(f"Content-Disposition: {content_disposition}")
                    # 尝试提取文件名
                    filename_match = re.search(r'filename\*?=([^;]+)', content_disposition)
                    if filename_match:
                        raw_filename = filename_match.group(1).strip('"')
                        # 处理URL编码的文件名
                        from urllib.parse import unquote
                        try:
                            filename = unquote(raw_filename)
                            file_path = os.path.join(self.save_dir, filename)
                            print(f"从响应头提取的文件名: {filename}")
                        except Exception as filename_error:
                            print(f"解析文件名时出错: {str(filename_error)}")
                
                # 检查文件大小
                file_size = int(response.headers.get('Content-Length', 0))
                print(f"文件大小: {file_size} 字节")
                
                # 保存文件
                with open(file_path, 'wb') as file:
                    for chunk in response.iter_content(chunk_size=8192):
                        if chunk:
                            file.write(chunk)
                
                # 验证文件是否成功写入
                if os.path.exists(file_path):
                    actual_size = os.path.getsize(file_path)
                    print(f"文件下载成功: {file_path}")
                    print(f"实际保存大小: {actual_size} 字节")
                    return file_path
                else:
                    print(f"文件保存失败，文件不存在: {file_path}")
                    return None
            else:
                print(f"下载失败，状态码: {response.status_code}")
                # 打印响应内容的一部分以便调试
                print(f"响应内容预览: {response.text[:500]}...")
                return None
        except Exception as e:
            print(f"下载文件时出错: {str(e)}")
            import traceback
            print(traceback.format_exc())
            return None
    
    def download_with_captcha(self, hcno):
        """完整的下载流程，包括获取验证码、验证和下载"""
        print(f"\n开始处理文件ID: {hcno}")
        
        # 最多尝试3次
        max_attempts = 3
        for attempt in range(max_attempts):
            print(f"\n尝试 {attempt + 1}/{max_attempts}")
            
            # 获取验证码图片和OCR识别结果
            captcha = self.get_captcha(hcno)
            
            # 保存验证码图片
            print("\n验证码图片已保存为 captcha.png 和 processed_captcha.png")
            
            # 尝试显示验证码图片
            try:
                # 使用PIL显示图片
                if os.path.exists('captcha.png'):
                    print("正在显示验证码图片...")
                    captcha_image = Image.open('captcha.png')
                    captcha_image.show()
                    print("验证码图片已显示，请查看")
                else:
                    print("验证码图片不存在，请检查captcha.png文件")
            except Exception as img_error:
                print(f"显示图片时出错: {str(img_error)}")
                print("请手动打开captcha.png文件查看验证码")
            
            # 显示OCR识别结果作为默认值
            if captcha:
                print(f"OCR自动识别的验证码: {captcha}")
                print("直接按Enter接受OCR结果，或输入正确的验证码:")
                user_captcha = input(f"验证码 [{captcha}]: ") or captcha
            else:
                print("OCR识别失败，请手动输入验证码:")
                user_captcha = input("请输入验证码: ")
            
            if user_captcha.strip():
                print(f"您输入的验证码: {user_captcha}")
                
                # 验证验证码
                download_link = self.verify_captcha(hcno, user_captcha)
                if download_link:
                    # 下载文件
                    return self.download_file(download_link, hcno)
                else:
                    print("验证码验证失败，请重新尝试...")
            else:
                print("您没有输入验证码，请重新尝试...")
        
        print(f"所有验证码尝试失败，无法下载文件: {hcno}")
        
        # 提供一个基于网页分析的下载链接尝试
        print("\n尝试直接构造下载链接...")
        # 一些网站可能使用固定模式的下载链接
        direct_download_links = [
            f"http://c.gb688.cn/bzgk/gb/getGbFile?type=download&hcno={hcno}",
            f"http://c.gb688.cn/bzgk/gb/downloadGb?hcno={hcno}",
            f"http://c.gb688.cn/bzgk/gb/fileDownload?hcno={hcno}",
            f"http://c.gb688.cn/bzgk/gb/download?hcno={hcno}"
        ]
        
        for link in direct_download_links:
            print(f"尝试直接下载链接: {link}")
            result = self.download_file(link, hcno)
            if result:
                return result
            time.sleep(1)
        
        # 提示用户手动操作
        print("\n建议手动操作步骤:")
        print(f"1. 打开浏览器访问: {self.download_base_url}{hcno}")
        print("2. 输入验证码")
        print("3. 点击下载按钮")
        print(f"4. 将文件保存到: {self.save_dir}")
        
        # 由于在自动化环境中难以完全绕过验证码，我们可以提供一个基于Selenium的解决方案
        print("\n自动化解决方案:")
        print("1. 安装Selenium: pip install selenium")
        print("2. 下载并配置ChromeDriver")
        print("3. 使用Selenium自动化浏览器操作，处理验证码")
        return None
    
    def extract_info_ids(self, html_content):
        """从HTML内容中提取唯一的查看详细按钮ID值"""
        if not html_content:
            return []
        
        # 直接使用正则表达式在整个HTML中搜索所有ID
        pattern = r"showInfo\('([^']+)'\)"
        all_matches = re.findall(pattern, html_content)
        
        # 使用set去除重复的ID值
        unique_ids = list(set(all_matches))
        
        return unique_ids
    
    def run(self):
        """运行爬虫主程序"""
        print(f"正在爬取网页: {self.base_url}")
        html_content = self.fetch_page()
        
        if html_content:
            print("提取ID值中...")
            unique_ids = self.extract_info_ids(html_content)
            
            if unique_ids:
                print(f"\n成功提取到 {len(unique_ids)} 个唯一ID值:")
                for idx, info_id in enumerate(unique_ids, 1):
                    print(f"{idx}. {info_id}")
                     # 提示用户输入要下载的ID
                    print("\n请输入要下载的ID:")
                    print(f"使用示例ID: {info_id}")      
                    # 执行下载流程
                    self.download_with_captcha(info_id)
                
               

                
                return unique_ids
            else:
                print("\n未找到任何ID值")
                return []
        else:
            print("\n无法获取网页内容")
            return []
    
    def download_by_hcno(self, hcno):
        """直接通过hcno下载文件的接口"""
        return self.download_with_captcha(hcno)

# 添加命令行执行功能
if __name__ == "__main__":
    spider = GBStdSpider()
    spider.run()