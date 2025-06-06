/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.patch;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.biz.RangerBizUtil;
import org.apache.ranger.biz.ServiceDBStore;
import org.apache.ranger.common.JSONUtil;
import org.apache.ranger.common.RangerValidatorFactory;
import org.apache.ranger.common.StringUtil;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.entity.XXServiceDef;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.model.validation.RangerServiceDefValidator;
import org.apache.ranger.plugin.model.validation.RangerValidator;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import org.apache.ranger.service.RangerPolicyService;
import org.apache.ranger.service.XPermMapService;
import org.apache.ranger.service.XPolicyService;
import org.apache.ranger.util.CLIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PatchForOzoneServiceDefConfigUpdate_J10051 extends BaseLoader {
    private static final Logger logger = LoggerFactory.getLogger(PatchForOzoneServiceDefConfigUpdate_J10051.class);

    @Autowired
    RangerDaoManager daoMgr;

    @Autowired
    ServiceDBStore svcDBStore;

    @Autowired
    JSONUtil jsonUtil;

    @Autowired
    RangerPolicyService policyService;

    @Autowired
    StringUtil stringUtil;

    @Autowired
    XPolicyService xPolService;

    @Autowired
    XPermMapService xPermMapService;

    @Autowired
    RangerBizUtil bizUtil;

    @Autowired
    RangerValidatorFactory validatorFactory;

    public static void main(String[] args) {
        logger.info("main()");

        try {
            PatchForOzoneServiceDefConfigUpdate_J10051 loader = (PatchForOzoneServiceDefConfigUpdate_J10051) CLIUtil.getBean(PatchForOzoneServiceDefConfigUpdate_J10051.class);

            loader.init();

            while (loader.isMoreToProcess()) {
                loader.load();
            }

            logger.info("Load complete. Exiting.");

            System.exit(0);
        } catch (Exception e) {
            logger.error("Error loading", e);

            System.exit(1);
        }
    }

    @Override
    public void init() throws Exception {
        // Do Nothing
    }

    @Override
    public void printStats() {
        logger.info("PatchForOzoneServiceDefConfigUpdate data ");
    }

    @Override
    public void execLoad() {
        logger.info("==> PatchForOzoneServiceDefConfigUpdate.execLoad()");

        try {
            if (!updateOzoneServiceDef()) {
                logger.error("Failed to apply the patch.");

                System.exit(1);
            }
        } catch (Exception e) {
            logger.error("Error while updateOzoneServiceDef()data.", e);

            System.exit(1);
        }

        logger.info("<== PatchForOzoneServiceDefConfigUpdate.execLoad()");
    }

    protected Map<String, String> jsonStringToMap(String jsonStr) {
        Map<String, String> ret = null;

        if (!StringUtils.isEmpty(jsonStr)) {
            try {
                ret = jsonUtil.jsonToMap(jsonStr);
            } catch (Exception ex) {
                // fallback to earlier format: "name1=value1;name2=value2"
                for (String optionString : jsonStr.split(";")) {
                    if (StringUtils.isEmpty(optionString)) {
                        continue;
                    }

                    String[] nvArr = optionString.split("=");
                    String   name  = nvArr.length > 0 ? nvArr[0].trim() : null;
                    String   value = nvArr.length > 1 ? nvArr[1].trim() : null;

                    if (StringUtils.isEmpty(name)) {
                        continue;
                    }

                    if (ret == null) {
                        ret = new HashMap<>();
                    }

                    ret.put(name, value);
                }
            }
        }
        return ret;
    }

    private boolean updateOzoneServiceDef() throws Exception {
        RangerServiceDef embeddedOzoneServiceDef = EmbeddedServiceDefsUtil.instance().getEmbeddedServiceDef(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_OZONE_NAME);

        if (embeddedOzoneServiceDef != null) {
            XXServiceDef xXServiceDefObj = daoMgr.getXXServiceDef().findByName(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_OZONE_NAME);

            if (xXServiceDefObj == null) {
                logger.error("Ozone service-definition does not exist in the Ranger DAO. No patching is needed!!");
                return true;
            }

            String              jsonPreUpdate              = xXServiceDefObj.getDefOptions();
            Map<String, String> serviceDefOptionsPreUpdate = jsonStringToMap(jsonPreUpdate);
            RangerServiceDef    dbOzoneServiceDef          = svcDBStore.getServiceDefByName(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_OZONE_NAME);

            if (dbOzoneServiceDef != null) {
                // Update old Ozone configs
                List<RangerServiceDef.RangerServiceConfigDef> embeddedOzoneConfigDefs = embeddedOzoneServiceDef.getConfigs();

                for (RangerServiceDef.RangerServiceConfigDef configDef : embeddedOzoneConfigDefs) {
                    if (StringUtils.equalsIgnoreCase(configDef.getName(), "hadoop.security.authorization")) {
                        configDef.setMandatory(false);
                        break;
                    }
                }

                dbOzoneServiceDef.setConfigs(embeddedOzoneConfigDefs);
            } else {
                logger.error("Ozone service-definition does not exist in the db store.");

                return false;
            }
            RangerServiceDefValidator validator = validatorFactory.getServiceDefValidator(svcDBStore);

            validator.validate(dbOzoneServiceDef, RangerValidator.Action.UPDATE);

            RangerServiceDef ret = svcDBStore.updateServiceDef(dbOzoneServiceDef);

            if (ret == null) {
                throw new RuntimeException("Error while updating " + EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_OZONE_NAME + " service-def");
            }

            xXServiceDefObj = daoMgr.getXXServiceDef().findByName(EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_OZONE_NAME);

            if (xXServiceDefObj != null) {
                String              jsonStrPostUpdate           = xXServiceDefObj.getDefOptions();
                Map<String, String> serviceDefOptionsPostUpdate = jsonStringToMap(jsonStrPostUpdate);

                if (serviceDefOptionsPostUpdate != null && serviceDefOptionsPostUpdate.containsKey(RangerServiceDef.OPTION_ENABLE_DENY_AND_EXCEPTIONS_IN_POLICIES)) {
                    if (serviceDefOptionsPreUpdate == null || !serviceDefOptionsPreUpdate.containsKey(RangerServiceDef.OPTION_ENABLE_DENY_AND_EXCEPTIONS_IN_POLICIES)) {
                        String preUpdateValue = serviceDefOptionsPreUpdate == null ? null : serviceDefOptionsPreUpdate.get(RangerServiceDef.OPTION_ENABLE_DENY_AND_EXCEPTIONS_IN_POLICIES);

                        if (preUpdateValue == null) {
                            serviceDefOptionsPostUpdate.remove(RangerServiceDef.OPTION_ENABLE_DENY_AND_EXCEPTIONS_IN_POLICIES);
                        } else {
                            serviceDefOptionsPostUpdate.put(RangerServiceDef.OPTION_ENABLE_DENY_AND_EXCEPTIONS_IN_POLICIES, preUpdateValue);
                        }

                        xXServiceDefObj.setDefOptions(mapToJsonString(serviceDefOptionsPostUpdate));

                        daoMgr.getXXServiceDef().update(xXServiceDefObj);
                    }
                }
            } else {
                logger.error("Ozone service-definition does not exist in the Ranger DAO.");

                return false;
            }
        } else {
            logger.error("The embedded Ozone service-definition does not exist.");

            return false;
        }

        return true;
    }

    private String mapToJsonString(Map<String, String> map) {
        String ret = null;

        if (map != null) {
            try {
                ret = jsonUtil.readMapToString(map);
            } catch (Exception ex) {
                logger.warn("mapToJsonString() failed to convert map: {}", map, ex);
            }
        }

        return ret;
    }
}
