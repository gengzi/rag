import com.gengzi.RagServerApplication;
import com.gengzi.embedding.load.JsonReaderUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = RagServerApplication.class)
public class JsonReaderUtilsTest {

    @Autowired
    private JsonReaderUtils jsonReaderUtils;


    @Test
    void testLoadJsonAsDocuments() {
        List<Document> documents = jsonReaderUtils.loadJsonAsDocuments();
        /**
         * [Document {
         *   id = 'e74a510b-bf6a-4a49-a376-749092aac096',
         *   text = '{parameters=[{parameterName=userInfo, description=用户基础信息参数，用于标识用户身份、偏好及基础配置，支持系统个性化服务与数据关联, content={userId=用户唯一标识ID（字符串格式，由系统自动生成，示例："U20250001"）, userType=用户类型（用于区分用户权限与服务范围）, preferredLanguage=用户偏好语言（影响系统界面与信息推送语言）},
         *   list={userTypeOptions=[ordinary_user, vip_user, admin_user], preferredLanguageOptions=[zh-CN, en-US, ja-JP, ko-KR]}}, {parameterName=productConfig, description=产品配置参数，用于定义产品功能开关、性能阈值及展示规则，支撑产品核心功能运行与灵活调整, content={productId=产品唯一标识ID（字符串格式，示例："P001_Electronic"）, functionStatus=功能开关状态（控制产品特定功能的启用/禁用）, maxConcurrentNum=最大并发数（产品支持的同时在线/操作上限，数值类型）}, list={functionStatusOptions=[enabled, disabled, trial], maxConcurrentNumRange=[100, 500, 1000, 2000]}}]}', media = 'null', metadata = {}, score = null
         * }]
         */
        System.out.println(documents);
    }


}
