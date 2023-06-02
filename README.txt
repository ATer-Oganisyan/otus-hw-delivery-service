В данном сервисе используется ключ идемпотентности. 
А именно, для создания элемента каталога используется уникальный код товара (иначе говоря, артикул).
Если передается уже существующий код элемента, то в ответ отдается 409 Conflict.

Для создания заказа используется заголовок x-request-id. Если создается задать с уже существующим request_id (который берется из заголовка), то возвращается 200OK с сообщением, что этот заказ уже был создан.

Отличие от каталога в том, что в случае каталога, пользователь сам должен указать код товара. И если он указывает существующий, то надо явно это указать, вернув ошибку 409.

В случае с заказами, x-request-id генерируется фронтенд приложением, и в случае повтора, вероятно, был retry из-за, например, прерванного соединения. В таком случае, клиент не должен получить ошибку, а просто должен получить сообщение, что заказ с таким reques-id уже существует.



Installation manual

Run:

alias k=kubectl
git clone https://github.com/ATer-Oganisyan/otus-hw-order-service.git
cd ./otus-hw-order-service
helm repo add nginx-stable https://helm.nginx.com/stable
helm repo add myZql https://charts.bitnami.com/bitnami
helm repo update
helm install my-release nginx-stable/nginx-ingress		
helm install myzql-release myZql/mysql -f kuber/mysql/values.yml
k apply -f ./kuber/config/
k apply -f ./kuber/mysql/migrations/  
k apply -f ./kuber/app

Import OrderServiceCollection.postman_collection.json into Postman.

Enjoy :)




To build app container run:

docker build -t arsenteroganisyan/stock-service:v20 /Users/arsen/otus-hw-stock-service --no-cache --platform linux/amd64




To build DB migration container:
 
docker build -t arsenteroganisyan/otus-stock-service-sql-migrator:v1 /Users/arsen/otus-hw-stock-service/kuber/mysql/migrations --no-cache --platform linux/amd64
