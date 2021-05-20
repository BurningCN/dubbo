DubboShutdownHook 
	run
		ShutdownHookCallbacks # callback

			DubboBootstrap # destroy

				DubboShutdownHook.destroyAll();

					AbstractRegistryFactory.destroyAll()

			 			for (Registry registry : getRegistries()) 

			                registry.destroy();

			                	AbstractRegistry # destroy

				                	
			                		// unregister
			                		for (URL url : new HashSet<>(getRegistered())) {

					                	AbstractRegistry # unregister
					                		registered.remove(url);
					                	
					                	FailbackRegistry # unregister
											super.unregister(url);
											removeFailedRegistered(url);
											removeFailedUnregistered(url);
					                		doUnregister
					                			zkClient.delete(toUrlPath(url));
					                				toCategoryPath(url) + "/" + URL.encode(url.toFullString());
					                				// toCategoryPath  eg /dubbo/{path}/{category}

				                	// unsubscribe
				                	for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) {
				                		for (NotifyListener listener : entry.getValue())

						                	AbstractRegistry # unsubscribe(url, listener)
						                		subscribed.get(url).remove(listener)
						                		notified.remove(url);

						                	FailbackRegistry # unsubscribe(url, listener)
						                		super.unsubscribe(url, listener)
						                		removeFailedSubscribed(url, listener)
						                		doUnsubscribe
						                			 zkClient.removeChildListener(path, zkListener); // ChildListener

						            // remove myself    			 	
						            AbstractRegistryFactory.removeDestroyedRegistry(this);


				                FailbackRegisty # destroy

				                	super.destroy();
        							retryTimer.stop();

        						ZookeeperRegistry

        							super.destroy();

						            zkClient.close();

						            	AbstractZookeeperClient # close

						            		closed = true

						            		CuratorZookeeperClient # doClose

						            				client.close();// CuratorFramework

					destroyProtocols();
						dubbo + inJvm + registry

						dubbo
							ProtocolFilterWrapper # destroy

								ProtocolListenerWrapper # destroy

									DubboProtocol destory

										// 1 provider 
										for (String key : new ArrayList<>(serverMap.keySet())) 

											 RemotingServer server = serverMap.remove(key).getRemotingServer();

											 	 server.close(ConfigurationUtils.getServerShutdownTimeout()); 

											 	  	HeaderExchangeServer close

											 	  		startClose 

											 	  			 server.startClose(); // server 为 nettyServer

											 	  			 	AbstractPeer startClose

											 	  			 		closing = true;

											 	  		sendChannelReadOnlyEvent();

											 	  			new Request() + request.setEvent("R") 

											 	  			 Collection<Channel> channels = getChannels()
										 	  					getExchangeChannels()
										 	  						for (Channel channel : server.getChannels())
										 	  							HeaderExchangeChannel.getOrAddChannel(channel)

											 	  			for (Channel channel : channels)
											 	  				
											 	  				channel.send

											 	  				// ========================== 

											 	  				client端 HeaderExchangeHandler # received

											 	  					handlerEvent

											 	  						channel.setAttribute("channel.readonly", Boolean.TRUE);

											 	  		while (HeaderExchangeServer.this.isRunning()
											                    && System.currentTimeMillis() - start < max) {
											                    Thread.sleep(10);
											                isRunning
											                	Collection<Channel> channels = getChannels()
											                		for (Channel channel : channels)
											                			if (channel.isConnected()) 
											                				return true

											            HeaderExchangeServer # doClose()

											            	closed cas true  + closeTimerTask.cancel();

											            server.close - nettyServer

											            	AbstractServer close(int timeout) 

												            	ExecutorUtil.gracefulShutdown(executor, timeout);

												            		es.shutdown();

												            		if (!es.awaitTermination(timeout, TimeUnit.MILLISECONDS)) 
               															es.shutdownNow(); 

               														if (!isTerminated(es))
            															newThreadToCloseExecutor(es);

            																if (!isTerminated(es))
            																	run:
            																		for (int i = 0; i < 1000; i++)
            																			es.shutdownNow()
            																			if (es.awaitTermination(10
            																				break

	        													AbstractServer close()

	        														ExecutorUtil.shutdownNow(executor, 100);

	        														super.close(); // AbstractPeer
	        															closed = true;

	        														doClose()  // this 为 nettyServer 

	        															channel.close(); // unbind channel为netty.io

	        															bossGroup.shutdownGracefully().syncUninterruptibly();
														                workerGroup.shutdownGracefully().syncUninterruptibly();

														                channels.clear(); // channel为NettyChannel


									    // 2 consumer
										for (String key : new ArrayList<>(referenceClientMap.keySet()))  // key为ip port

											for (ReferenceCountExchangeClient client : typedClients) 
												
												Object clients = referenceClientMap.remove(key)
												
												typedClients = (List<ReferenceCountExchangeClient>) clients

												for (ReferenceCountExchangeClient client : typedClients)

							                     closeReferenceCountExchangeClient(client);

							                    	client.close(ConfigurationUtils.getServerShutdownTimeout()); 

							                    		referenceCount.decrementAndGet() <= 0

								                    		client.close(timeout);// client 为 HeaderExchangeClient

								                    			startClose();
								                    				channel.startClose // channel 为 HeaderExchangeChannel
								                    					channel.startClose // channel 为 NettyClient
								                    						AbstractPeer startClose
								                    							closing = true;

														        doClose();
														        	heartBeatTimerTask.close 
														        	reconnectTimerTask.close

														        channel.close(timeout); // channel为HeaderExchangeChannel
														        	
														        	closed=true

														        	if (timeout > 0) {
														        		while (DefaultFuture.hasFuture(channel)
													                    	&& System.currentTimeMillis() - start < timeout) {
													                    	Thread.sleep(10);

													                    	DefaultFuture # hasFuture 

													                    		CHANNELS.containsValue(channel)
													            	close()
													            		DefaultFuture.closeChannel(channel)

													            			for(CHANNELS.entrySet())
													            			
													            				DefaultFuture future = getFuture(entry.getKey())
													            				futureExecutor.shutdownNow()
													            				Response disconnectResponse = new Response
													            				DefaultFuture.received(channel, disconnectResponse)

													            		channel.close()
								                    	
								                    		replaceWithLazyClient();


								        // 3
								        super.destroy(); -- AbstractProtocol

								        	// consumer
								        	for (Invoker<?> invoker : invokers) 

								        		invoker = invokers.remove(invoker)

								        		invoker.destroy() //  DubboInvoker

								        			DubboInvoker destroyed

								        				double-check for super.isDestroyed()

								        				super.destroy();
								        					cas true + setAvailable(false) 

								        				invokers.remove(this)

								        				for (ExchangeClient client : clients)

								        					client.close(ConfigurationUtils.getServerShutdownTimeout());
								        				


								        	// provider
								        	for (String key : new ArrayList<String>(exporterMap.keySet())) 

												Exporter<?> exporter = exporterMap.remove(key); // DubboExporter

												exporter.unexport(); // AbstractExporter 

													unexported = true;

											        getInvoker().destroy();

											        	AbstractProxyInvoker destroy
											        		// 空实现

											        afterUnExport();
											        	DubboExporter afterUnExport
											        		exporterMap.remove(key);

						injvm
							ProtocolFilterWrapper # destroy

								ProtocolListenerWrapper # destroy

									InjvmProtocol destory // injvmProtocol 没有重写destory，所以直接AbstractProtocol # destroy 和上面dubboProtocol的最后大步骤一致

					    registry
					    	ProtocolFilterWrapper # destroy

								ProtocolListenerWrapper # destroy

									RegistryProtocol # destory // this 为 InterfaceCompatibleRegistryProtocol
										
										for (RegistryProtocolListener listener : listeners) 
                							listener.onDestroy();

                								MigrationRuleListener # onDestroy

                									configuration.removeListener(MigrationRule.RULE_KEY, MigrationRule.DUBBO_SERVICEDISCOVERY_MIGRATION_GROUP, this); 
                									// znode /dubbo/config/MIGRATION/samples-annotation-provider.migration -> {CopyOnWriteArraySet@4234}  size = 1

                						List<Exporter<?>> exporters = new ArrayList<Exporter<?>>(bounds.values());
									    for (Exporter<?> exporter : exporters) 
									            exporter.unexport();

									            	RegistryProtocol # ExporterChangeableWrapper # unexport

									    bounds.clear();

				unregisterServiceInstance();

					getServiceDiscoveries().forEach // AbstractRegistryFactory # getServiceDiscoveries

						serviceDiscovery.unregister(serviceInstance);

							EventPublishingServiceDiscovery unregister

								ZookeeperServiceDiscovery unregister

									curator.ServiceDiscovery # unregisterService(build(serviceInstance));

                unexportMetadataService();

                	metadataServiceExporter.unexport()

                		serviceConfig.unexport()

                unexportServices();

	                exportedServices.forEach(sc -> {

			            configManager.removeConfig(sc);

			            sc.unexport();
			        });  // asyncExportingFutures 同理 

			        exportedServices.clear(); // asyncExportingFutures 同理 
			       

                unreferServices();

                destroyRegistries();

                	AbstractRegistryFactory.destroyAll();

                destroyServiceDiscoveries();

                	getServiceDiscoveries().forEach
			            execute(serviceDiscovery::destroy);

			        	EventPublishingServiceDiscovery destroy

			        		ZookeeperServiceDiscovery destroy

			        			curator.ServiceDiscovery # close

                destroyExecutorRepository();

                	DefaultExecutorRepository destroyAll

                		data.values().values() foreach

                			ExecutorUtil.shutdownNow(executor, 100);

                clear();
                	
                	clearConfigs();

                		configManager.destroy()

                			configsCache::clear
        			
        			clearApplicationModel();
        				// 空

                shutdown();

                	executorService.shutdown()

                release();

                	while (awaited.compareAndSet(false, true))
		                condition.signalAll();
		        DubboBootstrapStartStopListener # onStop(DubboBootstrap bootstrap);

		        	applicationContext.publishEvent(new DubboBootstrapStopedEvent(bootstrap));

		doDestroy

			dispatch(new DubboServiceDestroyedEvent(this));

				eventDispatcher.dispatch(event);



DubboBootstrapApplicationListener 
	onContextClosedEvent
	dubboBootstrap.stop();