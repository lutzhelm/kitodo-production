/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.data.elasticsearch.index.type;

import java.util.LinkedHashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;

/**
 * Implementation of BatchGroup Type.
 */
public class UserGroupType extends BaseType<UserGroup> {

    @SuppressWarnings("unchecked")
    @Override
    public HttpEntity createDocument(UserGroup userGroup) {

        JSONObject userGroupObject = new JSONObject();
        userGroupObject.put("title", userGroup.getTitle());
        userGroupObject.put("permission", userGroup.getPermission());

        JSONArray users = new JSONArray();
        List<User> userGroupUsers = userGroup.getUsers();
        for (User user : userGroupUsers) {
            JSONObject userObject = new JSONObject();
            userObject.put("id", user.getId());
            users.add(userObject);
        }

        userGroupObject.put("users", users);

        return new NStringEntity(userGroupObject.toJSONString(), ContentType.APPLICATION_JSON);
    }
}