.PHONY: build deploy clean

build:
	mvn clean package

deploy:
	mvn clean package -Pdeploy

clean:
	mvn clean
