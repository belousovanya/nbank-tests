package requests.skelethon.requesters;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import models.BaseModel;
import requests.skelethon.Endpoint;
import requests.skelethon.HttpRequest;
import requests.skelethon.interfaces.CrudEndpointInterface;
import requests.skelethon.interfaces.GetAllEndpointInterface;

import java.util.Arrays;
import java.util.List;

public class ValidatedCrudRequester<T extends BaseModel> extends HttpRequest implements CrudEndpointInterface, GetAllEndpointInterface {
    private CrudRequester crudRequester;

    public ValidatedCrudRequester(RequestSpecification requestSpecification, Endpoint endpoint, ResponseSpecification responseSpecification) {
        super(requestSpecification, endpoint, responseSpecification);
        this.crudRequester = new CrudRequester(requestSpecification, endpoint, responseSpecification);
    }

    @Override
    public T post() {
        return (T) crudRequester.post().extract().as(endpoint.getResponseModel());
    }

    @Override
    public T post(BaseModel model) {
        return (T) crudRequester.post(model).extract().as(endpoint.getResponseModel());
    }

    @Override
    public List<T> getAll() {
        // ответ — JSON-массив, поэтому читаем его как массив responseModel из endpoint
        T[] items = (T[]) crudRequester.getAll().extract().as(endpoint.getResponseModel().arrayType());
        return Arrays.asList(items);
    }

    @Override
    public Object get(long id) {
        return null;
    }

    @Override
    public Object put(long id, BaseModel model) {
        return null;
    }

    @Override
    public Object delete(long id) {
        return null;
    }
}
