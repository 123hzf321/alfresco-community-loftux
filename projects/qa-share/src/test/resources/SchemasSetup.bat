cd C:\Program Files\MySQL\MySQL Server 5.6\bin
mysql -h localhost -u alfresco --password=alfresco < C:\Users\jcule\Desktop\DropSchemas.sql
cd C:\Pentaho\design-tools\data-integration
kitchen.bat /file:C:\dev\integrations\Pentaho\HEAD\pentaho-etl\src\main\resources\ETL\schema_setup.kjb /level:Basic -param:db_name=alfresco -param:db_name_alfresco=alfresco -param_db_url_alfresco=jdbc:mysql://localhost:3306/alfresco
