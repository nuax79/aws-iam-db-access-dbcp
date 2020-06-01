package kr.misoboy.aws.datasource.dbcp;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.redshift.AmazonRedshift;
import com.amazonaws.services.redshift.AmazonRedshiftClientBuilder;
import com.amazonaws.services.redshift.model.GetClusterCredentialsRequest;
import com.amazonaws.services.redshift.model.GetClusterCredentialsResult;
import kr.misoboy.aws.datasource.BasicDataSourceHelper;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedshiftIamBasicDriverConnectionFactory implements ConnectionFactory {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Driver driver;
    private ApplicationContext applicationContext;
    private final Properties properties;
    private String connectionString;
    private String region;
    private String clusterId;

    /**
     * Constructs a connection factory for a given Driver.
     *  @param driver
     *            The Driver.
     * @param connectString
     *            The connection string.
     * @param properties
     */
    public RedshiftIamBasicDriverConnectionFactory(Driver driver, String connectString, Properties properties) {
        this.driver = driver;
        this.connectionString = connectString;
        this.properties = properties;
    }

    @Override
    public Connection createConnection() throws SQLException {
        generateAuthToken();
        return this.driver.connect(this.connectionString, this.properties);
    }

    private void generateAuthToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Generate Password Begin...");
        }

        BasicDataSourceHelper dataSourceHelper = BasicDataSourceHelper.getInstance();
        AWSCredentialsProvider awsCredentialsProvider = dataSourceHelper.getCredentialsProvider(this.applicationContext);

        String username = properties.getProperty("user");
        // Redshift Generate Password
        final Pattern urlPattern = Pattern.compile("jdbc:redshift:(iam:)?//([^:/?]+)(:([^/?]*))?(/([^?;]*))?([?;](.*))?");
        Matcher matcher = urlPattern.matcher(this.connectionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not valid jdbc connection url");
        }

        // redshift db name
        //String dbName = matcher.group(6);
        // redshift db options
        String dbParams = matcher.group(8);

        AmazonRedshiftClientBuilder redshiftClientBuilder = AmazonRedshiftClientBuilder.standard()
                .withCredentials(awsCredentialsProvider)
                .withRegion(region);

        AmazonRedshift amazonRedshift = redshiftClientBuilder.build();
        GetClusterCredentialsRequest clusterCredentialsRequest = new GetClusterCredentialsRequest()
                .withClusterIdentifier(clusterId)
                .withAutoCreate(false)
                .withDbUser(username);

        GetClusterCredentialsResult clusterCredentialsResult = amazonRedshift.getClusterCredentials(clusterCredentialsRequest);
        String password = clusterCredentialsResult.getDbPassword();
        this.connectionString += String.format("%suser=%s&password=%s", StringUtils.isEmpty(dbParams) ? "?" : "&", clusterCredentialsResult.getDbUser(), password);

        properties.setProperty("password", password);

        if (logger.isDebugEnabled()) {
            logger.debug("Redshift Password : {} ", password);
            logger.debug("Generate Password End...");
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }
}
