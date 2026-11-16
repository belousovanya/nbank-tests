package requests.skelethon.interfaces;

import models.BaseModel;

public interface CrudEndpointInterface {
    Object post();
    Object post(BaseModel model);
    Object get(long id);
    Object put(long id, BaseModel model);
    Object delete(long id);
}
