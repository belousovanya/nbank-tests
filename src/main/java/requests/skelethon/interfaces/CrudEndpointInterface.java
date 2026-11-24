package requests.skelethon.interfaces;

import models.BaseModel;

public interface CrudEndpointInterface {
    Object post();
    Object post(BaseModel model);
    Object get();
    Object put(BaseModel model);
    Object delete(long id);
}
