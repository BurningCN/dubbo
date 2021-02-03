package my.server;

/**
 * @author geyu
 * @date 2021/1/31 14:51
 */
public class PayloadDropper {
    public static Object getRequestWithoutData(Object message) {
        if (message instanceof Request) {
            Request request = (Request) message;
            request.setData(null);
            return request;
        } else if (message instanceof Response) {
            Response response = (Response) message;
            response.setResult(null);
            return response;
        }
        return message;
    }
}
