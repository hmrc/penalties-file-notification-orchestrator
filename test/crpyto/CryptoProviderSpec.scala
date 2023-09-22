/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package crpyto

import base.SpecBase
import com.typesafe.config.Config
import crypto.{CryptoProvider, NoCrypto}
import org.mockito.ArgumentMatchers
import play.api.Configuration

class CryptoProviderSpec extends SpecBase {
  val mockConfiguration: Configuration = mock[Configuration]
  val mockConfig: Config = mock[Config]

  class Setup {
    reset(mockConfiguration)
    reset(mockConfig)
    val cryptoProvider = new CryptoProvider(mockConfiguration)
  }

  "getCrypto" should {
    "use NoCrypto when the encryption feature switch is disabled" in new Setup {
      when(mockConfiguration.get[Boolean](ArgumentMatchers.eq("mongodb.encryption.enabled"))(ArgumentMatchers.any())).thenReturn(false)
      val result = cryptoProvider.getCrypto
      result.isInstanceOf[NoCrypto.type] shouldBe true
    }

    "use AES GCM Crypto when the encryption feature switch is enabled" in new Setup {
      when(mockConfiguration.get[Boolean](ArgumentMatchers.eq("mongodb.encryption.enabled"))(ArgumentMatchers.any())).thenReturn(true)
      when(mockConfiguration.underlying).thenReturn(mockConfig)
      when(mockConfig.getString(ArgumentMatchers.eq("mongodb.encryption.key"))).thenReturn("sample key")
      cryptoProvider.getCrypto
      //Anonymous class created in library - verifying a config call has been made is the next best alternative
      verify(mockConfig, times(1)).getString(ArgumentMatchers.eq("mongodb.encryption.key"))
    }
  }
}
